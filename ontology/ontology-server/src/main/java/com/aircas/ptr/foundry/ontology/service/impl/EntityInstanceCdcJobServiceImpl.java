package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologyInstanceConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyInstanceDTO;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyInstanceRepository;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import com.aircas.ptr.foundry.ontology.service.EntityInstanceCdcJobService;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据湖（entity_datasource 库）DML CDC -> ontology_instance 索引 + ArangoDB 图库同步。
 * <p>
 * 主表/从表判定：某表若是至少一个本体"主键属性"的数据源表
 * （ontology_property 中 datasource_schema + datasource_id 命中该表且 is_primary_key=1），
 * 则为主表，行数据同步为实例文档；否则视为从表（关联属性表），暂不同步。
 * 一条记录可能同时是多个本体的主表（如继承场景），逐一同步。
 * <p>
 * 图库侧同步的是实例节点（{@code node}）：实例关系边（{@code relation}）不由这里生成，
 * 图库只保留真实成立的边，由行为函数判定、手动激活或导入导出写入。
 * ES 与图库各自独立成段处理，图库失败只记录日志、不影响实例文档写入。
 */
@Slf4j
@Service
public class EntityInstanceCdcJobServiceImpl implements EntityInstanceCdcJobService {

    @Autowired
    OntologyPropertyMapper propertyMapper;
    @Autowired
    OntologyMetaMapper metaMapper;
    @Autowired
    OntologySpaceMapper spaceMapper;
    @Autowired
    OntologyInstanceRepository ontologyInstanceRepository;
    @Autowired
    ElasticsearchOperations elasticsearchOperations;
    @Autowired
    EntityService entityService;

    /** 图库未启用时（foundry.integration.arangodb.enabled=false）跳过全部图同步 */
    @Value("${foundry.integration.arangodb.enabled:true}")
    boolean graphEnabled;

    @Override
    public boolean handleCreateCdc(String schema, String table, Map<String, Object> after) {
        if (after == null) {
            return false;
        }
        List<OntologyProperty> pkProps = findPrimaryKeyProps(schema, table);
        if (pkProps.isEmpty()) {
            // 从表：表中不含任何本体的主键属性字段，暂不同步
            log.debug("表[{}.{}]非本体主表（从表），跳过实例同步", schema, table);
            return true;
        }
        pkProps.forEach(pkProp -> syncInstance(schema, table, after, pkProp, true));
        return true;
    }

    @Override
    public boolean handleUpdateCdc(String schema, String table, Map<String, Object> before, Map<String, Object> after) {
        if (after == null) {
            return false;
        }
        List<OntologyProperty> pkProps = findPrimaryKeyProps(schema, table);
        if (pkProps.isEmpty()) {
            log.debug("表[{}.{}]非本体主表（从表），跳过实例同步", schema, table);
            return true;
        }
        for (OntologyProperty pkProp : pkProps) {
            String pkColumn = pkProp.getDatasourceColumnName();
            Object newPkValue = OntologyInstanceConverter.getColumnValue(after, pkColumn);
            Object oldPkValue = before == null ? null : OntologyInstanceConverter.getColumnValue(before, pkColumn);
            // 主键值被修改：旧 _id 的文档已无法定位更新，删除旧文档后按新主键重建
            boolean pkChanged = oldPkValue != null && newPkValue != null
                    && !Objects.equals(oldPkValue.toString(), newPkValue.toString());
            if (pkChanged) {
                deleteInstanceByPk(OntologyInstanceConverter.buildPk(
                        schema, table, pkProp.getOntologyUniqueIdentifier(), oldPkValue));
                deleteGraphNode(pkProp.getOntologyUniqueIdentifier(), oldPkValue);
            }
            // 主键变更等同于新增实例：旧节点及相邻边已删，按新主键重建节点
            syncInstance(schema, table, after, pkProp, pkChanged);
        }
        return true;
    }

    @Override
    public boolean handleDeleteCdc(String schema, String table, Map<String, Object> before) {
        if (before == null) {
            return false;
        }
        List<OntologyProperty> pkProps = findPrimaryKeyProps(schema, table);
        if (pkProps.isEmpty()) {
            log.debug("表[{}.{}]非本体主表（从表），跳过实例删除", schema, table);
            return true;
        }
        for (OntologyProperty pkProp : pkProps) {
            Object pkValue = OntologyInstanceConverter.getColumnValue(before, pkProp.getDatasourceColumnName());
            if (pkValue == null) {
                continue;
            }
            deleteInstanceByPk(OntologyInstanceConverter.buildPk(
                    schema, table, pkProp.getOntologyUniqueIdentifier(), pkValue));
            deleteGraphNode(pkProp.getOntologyUniqueIdentifier(), pkValue);
        }
        return true;
    }

    /**
     * 将一行主表数据写入（upsert）ontology_instance 索引。
     * save 走实体索引路径，routing 由 @Routing("pk") 从文档 pk 自动解析，满足 _routing.required。
     *
     * @param rebuildNode 是否按本体属性回查后完整重建图节点（湖侧新增行 / 主键变更重建传 true）
     */
    private void syncInstance(String schema, String table, Map<String, Object> row, OntologyProperty pkProp,
                              boolean rebuildNode) {
        String pkColumn = pkProp.getDatasourceColumnName();
        Object pkValue = OntologyInstanceConverter.getColumnValue(row, pkColumn);
        if (pkValue == null) {
            log.warn("表[{}.{}]行数据主键列[{}]为空，跳过实例同步", schema, table, pkColumn);
            return;
        }
        OntologyMeta meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, pkProp.getOntologyUniqueIdentifier())
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        if (meta == null) {
            log.warn("本体[{}]不存在或已删除，跳过表[{}.{}]实例同步",
                    pkProp.getOntologyUniqueIdentifier(), schema, table);
            return;
        }
        // 名称键与主键同表（业务约束），缺省时实例 name 取主键值（由转换器兜底）
        // 同 findPrimaryKeyProps：不按 datasource_schema 过滤（自动绑定路径不回填该列），本体+表已足够定位
        OntologyProperty titleProp = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, meta.getUniqueIdentifier())
                        .eq(OntologyProperty::getIsTitleKey, 1)
                        .eq(OntologyProperty::getDatasourceId, table)
                        .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()))
                .stream().findFirst().orElse(null);
        OntologySpace space = meta.getOntologySpaceId() == null
                ? null : spaceMapper.selectById(meta.getOntologySpaceId());
        Object titleValue = titleProp == null
                ? null : OntologyInstanceConverter.getColumnValue(row, titleProp.getDatasourceColumnName());

        EsOntologyInstanceDTO dto = OntologyInstanceConverter.convert(
                schema, table, pkValue, titleValue, row, meta, space);
        if (dto != null) {
            ontologyInstanceRepository.save(dto);
            log.info("数据湖[{}.{}]实例文档同步成功, pk={}", schema, table, dto.getPk());
            syncGraphNode(meta.getUniqueIdentifier(), table, row, pkValue, dto.getName(), rebuildNode);
        }
    }

    /**
     * 单行实例 -> 图节点（展示名与实例文档 name 同口径，避免检索与图两侧名称不一致）。
     * <p>
     * 湖侧新增行 / 主键变更重建走 {@code completeEntityNode}：它按本体主键属性取主键、按名称键属性取展示名，
     * 建（覆盖）节点。实例关系边不在这里生成——图库里只保留真实成立（ENABLE/DISABLE）的边，
     * 由行为函数判定、手动激活或导入导出点亮。
     * 普通字段更新只刷展示名，不回查本体属性；若刷名时发现图库里其实没有该节点（例如图库被清过），
     * {@code upsertEntityNode} 会直接建出来。
     *
     * @param rebuildNode 是否按本体属性回查后完整重建节点（湖侧新增行 / 主键变更重建传 true）
     */
    private void syncGraphNode(String ontologyUid, String table, Map<String, Object> row,
                               Object pkValue, String displayName, boolean rebuildNode) {
        if (!graphEnabled) {
            return;
        }
        try {
            if (rebuildNode) {
                // 既有接口：按本体的主键属性/名称键属性回查后建（覆盖）节点
                entityService.completeEntityNode(ontologyUid, row);
                log.info("数据湖实例图节点同步, ontology={}, pk={}", ontologyUid, pkValue);
                return;
            }
            // 普通字段更新只刷展示名
            if (entityService.upsertEntityNode(ontologyUid, table, pkValue, displayName)) {
                log.info("数据湖实例图节点补建（图库此前无此节点）, ontology={}, pk={}", ontologyUid, pkValue);
            }
        } catch (Exception e) {
            log.error("数据湖实例图节点同步失败, ontology={}, pk={}", ontologyUid, pkValue, e);
        }
    }

    /**
     * 删除图节点及其相邻关系边（湖行被删或主键变更时与实例文档保持同生同灭）。
     */
    private void deleteGraphNode(String ontologyUid, Object pkValue) {
        if (!graphEnabled) {
            return;
        }
        try {
            entityService.deleteEntityNodeAndRelations(ontologyUid, pkValue);
            log.info("数据湖实例图节点及相邻关系边删除, ontology={}, pk={}", ontologyUid, pkValue);
        } catch (Exception e) {
            log.error("数据湖实例图节点删除失败, ontology={}, pk={}", ontologyUid, pkValue, e);
        }
    }

    /**
     * 按 pk（= 文档 _id）删除实例文档。
     * 索引 _routing.required=true 时按 id 直接删除会因 routing 缺失失败，
     * 故使用 by-query 删除，ES 自动按命中携带 routing。
     */
    private void deleteInstanceByPk(String pk) {
        DeleteQuery deleteQuery = DeleteQuery.builder(NativeQuery.builder()
                        .withQuery(q -> q.term(t -> t.field("pk").value(pk)))
                        .build())
                .withRefresh(Boolean.TRUE)
                .build();
        var response = elasticsearchOperations.delete(deleteQuery, EsOntologyInstanceDTO.class);
        log.info("数据湖实例文档删除, pk={}, 删除 {} 条", pk, response.getDeleted());
    }

    /**
     * 查找以该表为主键数据源表、且真实 schema 命中事件 schema 的全部本体主键属性；
     * 为空即该表为从表（或未被启用本体绑定）。
     * <p>
     * 注意：不能按 {@code ontology_property.datasource_schema} 过滤。自动绑定路径
     * （{@code autoBindDatasource}）用 resolveSchemaName（本体→空间.apiName）建表，
     * 但从不回填 datasource_schema，该列停留在 DDL 默认值 'public'，与真实 schema（如 space_a）不符。
     * 因此先按表名捞出候选主键属性，再解析每个候选所属本体的真实 schema 与事件 schema 比对。
     */
    private List<OntologyProperty> findPrimaryKeyProps(String schema, String table) {
        List<OntologyProperty> candidates = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getDatasourceId, table)
                .eq(OntologyProperty::getIsPrimaryKey, 1)
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()));
        if (candidates.isEmpty()) {
            return candidates;
        }
        Set<String> ontologyUids = candidates.stream()
                .map(OntologyProperty::getOntologyUniqueIdentifier)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toSet());
        if (ontologyUids.isEmpty()) {
            return List.of();
        }
        // 启用中的本体：uid -> ontologySpaceId
        Map<String, Integer> uidToSpaceId = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .in(OntologyMeta::getUniqueIdentifier, ontologyUids)
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream()
                .filter(meta -> meta.getOntologySpaceId() != null)
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier,
                        OntologyMeta::getOntologySpaceId, (a, b) -> a));
        if (uidToSpaceId.isEmpty()) {
            return List.of();
        }
        // 空间：spaceId -> apiName（即数据湖真实 schema）
        List<Integer> spaceIds = uidToSpaceId.values().stream().distinct().collect(Collectors.toList());
        Map<Integer, String> spaceIdToApiName = spaceMapper.selectBatchIds(spaceIds).stream()
                .filter(space -> StringUtils.isNotEmpty(space.getApiName()))
                .collect(Collectors.toMap(OntologySpace::getId, OntologySpace::getApiName, (a, b) -> a));
        // 保留真实 schema 与事件 schema 一致的候选主键属性
        return candidates.stream()
                .filter(p -> {
                    Integer spaceId = uidToSpaceId.get(p.getOntologyUniqueIdentifier());
                    return spaceId != null && Objects.equals(spaceIdToApiName.get(spaceId), schema);
                })
                .collect(Collectors.toList());
    }
}
