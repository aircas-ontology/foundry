package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.composite.BasicQueryOperator;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.converter.OntologyInstanceConverter;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityNodeExportDTO;
import com.aircas.ptr.foundry.ontology.model.dto.EntityRelationExportDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyInstancesExportDTO;
import com.aircas.ptr.foundry.ontology.model.enums.*;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityNodeRepository;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityRelationRepository;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.DataLakeObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableFieldMappingMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionExecuteResultMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.ManualExecuteLogService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.aircas.ptr.foundry.ontology.utils.FunctionOutputValueReader;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.aircas.ptr.foundry.ontology.converter.DataConverter.convert2DataType;

/**
 * todo 添加事务
 *
 * @className: EntityServiceImpl
 * @author: yangj
 * @date: 2025/4/8 18:40
 * @version: 1.0
 * @description:
 */
@Slf4j
@Service
public class EntityServiceImpl implements EntityService {

    /**
     * 单条关系下实例关系边的导出上限，避免边量过大把导出文件撑爆。
     */
    private static final int EXPORT_RELATION_LIMIT = 5000;

    /**
     * 图节点集合名，与 {@link EntityNode} 的 @Document 一致，用于拼接关系边的端点标识。
     */
    private static final String NODE_COLLECTION = "node";

    /**
     * 通配出参（取值路径含 {@code [*]}）单次批量写回的行数封顶：
     * 超出说明算子返回了异常规模的数组，宁可报错也不把数据湖写爆。
     */
    private static final int MAX_WILDCARD_OUTPUT_ROWS = 100000;

    /**
     * 通配出参分批插入的批大小：一条 INSERT 塞几万行会顶到数据库的语句参数上限。
     */
    private static final int WILDCARD_INSERT_BATCH = 1000;

    /**
     * PostgreSQL 单条语句的绑定参数上限，{@code batchInsertObject} 每列每行各占一个，
     * 用于按列数反推一批最多能插多少行。
     */
    private static final int MAX_BIND_PARAMS_PER_STATEMENT = 65535;

    @Resource
    private OntologyMetaMapper metaMapper;

    @Resource
    private OntologyLinkGroupMapper linkGroupMapper;

    @Resource
    private FunctionService functionService;

    @Lazy
    @Resource
    private OntologyActionService actionService;

    @Resource
    private ManualExecuteLogService manualExecuteLogService;


    @Resource
    private OntologyPropertyMapper propertyMapper;

    @Resource
    private EntityRelationRepository relationRepository;

    @Resource
    private EntityNodeRepository nodeRepository;

    @Resource
    private TableFieldMappingMapper tableFieldMappingMapper;

    @Resource
    private DataLakeObjectMapper objectMapper;

    @Resource
    private TableMetadataMapper tableMetadataMapper;

    @Resource
    private FunctionExecuteResultMapper executeResultMapper;

    @Resource
    private TaskProcessor taskProcessor;


    /**
     * 函数执行结果的自定义键由脚本决定，已由 FunctionResultVO 的 @JsonAnySetter 承接；
     * 根节点形状由 {@link #readFunctionResult} 先判，避免标量/数组结果按 VO 硬解失败。
     */
    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 仅用于解析行为入参的固定值字面量：默认 mapper 读到第一个 JSON token 就返回，
     * TLE 两行根数这类「数字开头+空格+文字」会被静默截成前导数字，故要求整串都是 JSON。
     */
    private final com.fasterxml.jackson.databind.ObjectMapper fixedValueMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /**
     * 宽容解析算子执行结果。
     * <p>脚本返回标量（如 {@code a+b}）或数组时根节点不是对象，按 VO 反序列化会抛
     * MismatchedInputException，故非对象根返回 null。解析出的 VO 只用于判断异步 taskId
     * 与可见窗口（startTime/endTime），不参与属性回写。</p>
     */
    @SneakyThrows
    private FunctionResultVO readFunctionResult(String resultJson) {
        if (StringUtils.isEmpty(resultJson)) {
            return null;
        }
        // 显式 JsonNode，不写 var：readTree 在本版本 Jackson 下可能是泛型 <T extends TreeNode> T
        com.fasterxml.jackson.databind.JsonNode root = jsonMapper.readTree(resultJson);
        return root == null || !root.isObject() ? null : jsonMapper.treeToValue(root, FunctionResultVO.class);
    }

    /**
     * 算子是否为同步返回：同步则当场回写属性与关系，只有异步任务（带 taskId）才把落库交给回调。
     * <p>返回体形状不影响这个判断——标量/数组根同样要走到回写里，绑定了 WRITE_PROPERTY 出参却
     * 取不到值时由 {@link #writeFunctionOutputToProperties} 显式报错，不能静默跳过还回成功。</p>
     */
    private boolean isSyncFunctionResult(String resultJson) {
        if (StringUtils.isEmpty(resultJson)) {
            return false;
        }
        var functionResult = readFunctionResult(resultJson);
        return functionResult == null || StringUtils.isEmpty(functionResult.getTaskId());
    }


    @Override
    public List<EntityRelationExportDTO> exportEntityRelations(String linkUniqueIdentifier) {
        var edges = relationRepository.queryNonDeletedRelationsByLinkIdWithLimit(linkUniqueIdentifier, EXPORT_RELATION_LIMIT);
        if (edges.size() >= EXPORT_RELATION_LIMIT) {
            log.warn("关系 {} 实例关系边数量达到导出上限 {}，结果已截断", linkUniqueIdentifier, EXPORT_RELATION_LIMIT);
        }
        var exported = Lists.<EntityRelationExportDTO>newArrayList();
        edges.forEach(edge -> {
            if (edge.getFrom() == null || edge.getTo() == null) {
                log.warn("关系 {} 实例关系边 {} 导出跳过：端点节点已不存在", linkUniqueIdentifier, edge.getId());
                return;
            }
            exported.add(EntityRelationExportDTO.builder()
                    .entityPrimaryKeyFrom(pkKey(edge.getFrom().getPrimaryKey()))
                    .entityPrimaryKeyTo(pkKey(edge.getTo().getPrimaryKey()))
                    .status(edge.getStatus())
                    .startTime(edge.getStartTime())
                    .endTime(edge.getEndTime())
                    .timeWindows(edge.getTimeWindows())
                    .build());
        });
        return exported;
    }

    @Override
    public void importEntityRelations(String linkUniqueIdentifier,
                                      List<EntityRelationExportDTO> relations,
                                      Map<String, Object> fromPkMapping,
                                      Map<String, Object> toPkMapping) {
        if (CollectionUtils.isEmpty(relations)) {
            return;
        }
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkUniqueIdentifier));
        PreconditionUtils.checkArgument(link != null, "对象关系不存在:" + linkUniqueIdentifier);

        var fromNodes = nodesByPrimaryKey(link.getOntologyUniqueIdentifierFrom());
        var toNodes = nodesByPrimaryKey(link.getOntologyUniqueIdentifierTo());
        //先把导出边解析成新主键对应的图节点；图库里未点亮的实例对没有边，命中的按状态覆盖、缺失的直建
        var pending = Lists.<EntityRelation>newArrayList();
        var skipped = 0;
        for (var relation : relations) {
            var fromNode = fromNodes.get(mappedPkKey(fromPkMapping, relation.getEntityPrimaryKeyFrom()));
            var toNode = toNodes.get(mappedPkKey(toPkMapping, relation.getEntityPrimaryKeyTo()));
            if (fromNode == null || toNode == null) {
                skipped++;
                log.warn("关系 {} 实例关系边落位跳过：实例主键无对应图节点，from={}，to={}", linkUniqueIdentifier,
                        relation.getEntityPrimaryKeyFrom(), relation.getEntityPrimaryKeyTo());
                continue;
            }
            pending.add(EntityRelation.builder()
                    .ontologyLinkId(linkUniqueIdentifier)
                    .from(fromNode)
                    .to(toNode)
                    .status(relation.getStatus() == null ? Status.ENABLE : relation.getStatus())
                    .startTime(relation.getStartTime())
                    .endTime(relation.getEndTime())
                    .timeWindows(relation.getTimeWindows() == null
                            ? Lists.<VisibilityWindow>newArrayList() : relation.getTimeWindows())
                    .createTime(new Date())
                    .updateTime(new Date())
                    .type(link.getType())
                    .name(link.getName())
                    .build());
        }
        if (CollectionUtils.isEmpty(pending)) {
            log.warn("关系 {} 实例关系边落位跳过：导出的 {} 条边均无对应图节点", linkUniqueIdentifier, relations.size());
            return;
        }
        //已存在的端点对按导出状态覆盖，图库里没有的直建；不会为未点亮的实例对补边
        var nodePairs = pending.stream().map(this::nodePairKey).distinct().collect(Collectors.toList());
        var existEdges = relationRepository.queryRelationsByNodePairs(linkUniqueIdentifier, nodePairs).stream()
                .filter(edge -> edge.getFrom() != null && edge.getTo() != null)
                .collect(Collectors.toMap(this::nodePairKey, edge -> edge, (a, b) -> a));
        var newEdges = Lists.<EntityRelation>newArrayList();
        for (var edge : pending) {
            var exist = existEdges.get(nodePairKey(edge));
            if (exist != null) {
                relationRepository.updateRelation(edge.getStartTime(), edge.getEndTime(), edge.getTimeWindows(),
                        edge.getStatus(), exist.getId());
            } else {
                newEdges.add(edge);
            }
        }
        if (CollectionUtils.isNotEmpty(newEdges)) {
            relationRepository.batchSave(newEdges);
        }
        log.info("关系 {} 实例关系边落位完成：导出 {} 条，写入 {} 条，新建 {} 条，跳过 {} 条", linkUniqueIdentifier,
                relations.size(), pending.size(), newEdges.size(), skipped);
    }

    /**
     * 实例关系边的端点标识，形如 {@code node/1|node/2}，与 AQL 中 CONCAT(edge._from, '|', edge._to) 对齐。
     */
    private String nodePairKey(EntityRelation edge) {
        return NODE_COLLECTION + "/" + edge.getFrom().getId() + "|" + NODE_COLLECTION + "/" + edge.getTo().getId();
    }

    /**
     * 实例主键归一化：图库存 Long、JSON 反序列化可能是 Integer 或 String，统一按字符串比较。
     */
    private String pkKey(Object primaryKey) {
        return primaryKey == null ? null : String.valueOf(primaryKey).trim();
    }

    /**
     * 导出主键经映射换成新主键（归一化字符串），映射缺失或源主键为空时返回 null。
     */
    private String mappedPkKey(Map<String, Object> pkMapping, String sourcePrimaryKey) {
        if (pkMapping == null || sourcePrimaryKey == null) {
            return null;
        }
        return pkKey(pkMapping.get(sourcePrimaryKey));
    }

    private Map<String, EntityNode> nodesByPrimaryKey(String ontologyUniqueIdentifier) {
        return getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .filter(node -> node.getPrimaryKey() != null)
                .collect(Collectors.toMap(node -> pkKey(node.getPrimaryKey()), node -> node, (a, b) -> a));
    }


    /**
     * 标题健需要和主键为同一个数据源
     *
     */
    @Override
    public void syncNodes(String ontologyUniqueIdentifier, String schemaName, String datasourceId, String primaryKeyColumnName, String titleKeyColumnName) {

        //图库回读的 Object 主键会退化成 Integer，与数据湖查出的类型不一致，统一按字符串比较，否则同一实例会被建出重复节点
        var existNodesMap = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .filter(v -> v.getPrimaryKey() != null)
                .collect(Collectors.toMap(v -> pkKey(v.getPrimaryKey()), v -> v, (a, b) -> a));

        List<Map<String, Object>> allRows = objectMapper.queryPrimaryKeyAndTitleKeyValue(schemaName, datasourceId, primaryKeyColumnName, titleKeyColumnName);
        List<EntityNode> nodes = Lists.newArrayList();

        allRows.stream().forEach(r -> {
            var primaryKeyValue = r.get(primaryKeyColumnName);
            if (primaryKeyValue instanceof Integer) {
                primaryKeyValue = Long.parseLong(primaryKeyValue.toString());
            }
            var exist = existNodesMap.get(pkKey(primaryKeyValue));
            if (exist != null) {
                var oldDisplayName = exist.getDisplayName();
                var newDisplayName = StringUtils.isEmpty(titleKeyColumnName) ? r.get(primaryKeyColumnName).toString() : r.get(titleKeyColumnName).toString();
                if (!StringUtils.equals(oldDisplayName, newDisplayName)) {
                    exist.setDisplayName(newDisplayName);
                    nodes.add(exist);
                }
            } else {
                nodes.add(EntityNode.builder()
                        .ontologyUniqIdentifier(ontologyUniqueIdentifier)
                        .primaryKey(primaryKeyValue)
                        .tableName(datasourceId)
                        .displayName(StringUtils.isEmpty(titleKeyColumnName) ? r.get(primaryKeyColumnName).toString() : r.get(titleKeyColumnName).toString())
                        .build());
            }
        });

        if (CollectionUtils.isNotEmpty(nodes)) {
            nodeRepository.batchSave(nodes);
        }

    }

    @Override
    public void deleteRelationsByLinkId(String linkId) {
        relationRepository.deleteByOntologyLinkId(linkId);
    }

    @Override
    public void deleteNodesAndRelationsByOntologyId(String ontologyUniqueIdentifier) {
        var nodes = nodeRepository.findByOntologyUniqIdentifier(ontologyUniqueIdentifier);
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }
        nodeRepository.deleteByIds(nodes.stream().map(v -> v.getArangoId()).collect(Collectors.toList()));
        var relations = relationRepository.findByFrom(nodes);
        relations.addAll(relationRepository.findByTo(nodes));
        if (CollectionUtils.isEmpty(relations)) {
            return;
        }
        relationRepository.deleteByIds(relations.stream().map(v -> v.getArangoId()).collect(Collectors.toList()));
    }


    @Override
    public List<EntityLinkPropertyVO> getEntityLinksByPrimaryKey(String ontologyUniqueIdentifier,
                                                                 Object entityPrimaryKey) {

        //var relations = relationRepository.queryEnableRelationsByEntity(ontologyUniqueIdentifier, entityPrimaryKey);
        var relations = relationRepository.queryAllRelationsByEntities(ontologyUniqueIdentifier, Lists.newArrayList(entityPrimaryKey));
        if (CollectionUtils.isEmpty(relations)) {
            return Lists.newArrayList();
        }
        return relations.stream().map(v -> DataConverter.convert(v)).collect(Collectors.toList());
    }


    @Override
    public List<EntityLinkPropertyVO> getEntityLinksBetween(String fromOntologyUniqueIdentifier, Object fromEntityPrimaryKey,
                                                            String toOntologyUniqueIdentifier, Object toEntityPrimaryKey) {
        var relations = relationRepository.queryRelationsBetweenEntities(
                fromOntologyUniqueIdentifier, fromEntityPrimaryKey, toOntologyUniqueIdentifier, toEntityPrimaryKey);
        if (CollectionUtils.isEmpty(relations)) {
            return Lists.newArrayList();
        }
        return relations.stream().map(v -> DataConverter.convert(v)).collect(Collectors.toList());
    }


    @Override
    public List<EntityLinksVO> getAllLinksByEntityIds(List<EntityIdsQueryParam> params) {
        List<EntityLinksVO> res = Lists.newArrayList();
        params.stream().forEach(p -> {
            var primaryKeys = p.getEntityPrimaryKeys();
            if (CollectionUtils.isEmpty(primaryKeys)) {
                var entities = getByEntityIds(Lists.newArrayList(EntityIdsQueryParam
                        .builder()
                        .ontologyUniqueIdentifier(p.getOntologyUniqueIdentifier())
                        .build()));
                primaryKeys = entities.stream().flatMap(v -> v.getEntityList().stream().map(e -> e.getPrimaryKey())).collect(Collectors.toList());
            }
            var relations = relationRepository.queryAllRelationsByEntities(p.getOntologyUniqueIdentifier(), primaryKeys);

            if (CollectionUtils.isEmpty(relations)) {
                return;
            }

            var map1 = relations.stream().collect(Collectors.groupingBy(v -> v.getFrom().getOntologyUniqIdentifier() + v.getFrom().getPrimaryKey()));
            var map2 = relations.stream().collect(Collectors.groupingBy(v -> v.getTo().getOntologyUniqIdentifier() + v.getTo().getPrimaryKey()));

            map2.forEach((key, list) -> map1.merge(key, list, (list1, list2) -> {
                list1.addAll(list2);
                return list1;
            }));

            primaryKeys.forEach(pk -> {
                var r = map1.get(p.getOntologyUniqueIdentifier() + pk);
                res.add(EntityLinksVO.builder()
                        .links(r.stream().map(v -> DataConverter.convert(v)).collect(Collectors.toList()))
                        .entityPrimaryKey(pk)
                        .ontologyUniqueIdentifier(p.getOntologyUniqueIdentifier())
                        .build());
            });

        });
        return res;
    }

    @Override
    public EntityNode findOneByOntologyUniqIdentifier(String ontologyIdentifier) {
        return nodeRepository.findOneByOntologyUniqIdentifier(ontologyIdentifier);
    }

    @Override
    public List<EntityPropertyDetailVO> getEntityDetail(String ontologyUniqueIdentifier, Object entityPrimaryKey) {
        return getEntityDetail(ontologyUniqueIdentifier, entityPrimaryKey, null, null);
    }

    /** 扩展分组没指定行数时的默认读取行数（按行主键倒序） */
    private static final int DEFAULT_GROUP_ROWS = 10;

    /**
     * 取实例属性详情。rowKeysByGroup 指定的分组按行精确取值，值可以是行主键数组
     * （LIST 入参按元素给行时走这支，按数组顺序逐行取），分组名 -> 行主键；
     * 没指定的分组沿用原口径（扩展表按主键倒序取行，行数按 maxRowsByProperty 给的取，默认 10 行）。
     */
    @Override
    public List<EntityPropertyDetailVO> getEntityDetail(String ontologyUniqueIdentifier, Object entityPrimaryKey,
                                                        Map<String, Object> rowKeysByGroup) {
        return getEntityDetail(ontologyUniqueIdentifier, entityPrimaryKey, rowKeysByGroup, null, null);
    }

    @Override
    public List<EntityPropertyDetailVO> getEntityDetail(String ontologyUniqueIdentifier, Object entityPrimaryKey,
                                                        Map<String, Object> rowKeysByGroup,
                                                        Map<String, Integer> maxRowsByProperty) {
        return getEntityDetail(ontologyUniqueIdentifier, entityPrimaryKey, rowKeysByGroup, maxRowsByProperty, null);
    }

    /**
     * 按入参读取规则读子表：listReadRules 是「存储分组 -&gt; 规则」，只对没指定行的分组生效，
     * 规则里的行数、排序属性与方向覆盖默认口径。
     */
    public List<EntityPropertyDetailVO> getEntityDetail(String ontologyUniqueIdentifier, Object entityPrimaryKey,
                                                        Map<String, Object> rowKeysByGroup,
                                                        Map<String, Integer> maxRowsByProperty,
                                                        Map<String, ActionTestListReadRuleParam> listReadRules) {
        List<EntityPropertyDetailVO> res = Lists.newArrayList();
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier))
                .stream()
                .filter(v -> StringUtils.isNotEmpty(v.getDatasourceColumnName()))
                .collect(Collectors.toList());
        checkGroupRowKeys(ontologyUniqueIdentifier, props, rowKeysByGroup);

        var primaryKeyProp = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        if (!primaryKeyProp.isPresent() || StringUtils.isEmpty(primaryKeyProp.get().getDatasourceColumnName())) {
            return res;
        }
        var propsMap = props.stream().collect(Collectors.groupingBy(v -> v.getDatasourceId()));
        //查询主键表对应的实体数据
        var pk = primaryKeyProp.get();
        var pkColumns = propsMap.get(pk.getDatasourceId()).stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList());
        var entityPrimaryKeyValue = datalakeKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pk.getDatasourceColumnName(), entityPrimaryKey);
        var primaryData = objectMapper.queryDataByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pkColumns, pk.getDatasourceColumnName(), entityPrimaryKeyValue);
        var propertyMap = propsMap.get(pk.getDatasourceId()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        var details = primaryData.entrySet().stream().<EntityPropertyDetailVO>map(entry -> {
            var colName = entry.getKey();
            var colValue = entry.getValue();
            var p = propertyMap.get(colName);
            return EntityPropertyDetailVO.builder()
                    .propertyDisplayName(p.getDisplayName())
                    .propertyValues(Lists.newArrayList(colValue))
                    .propertyUniqIdentifier(p.getUniqueIdentifier())
                    .propertyApiName(p.getApiName())
                    .entityPrimaryKey(entityPrimaryKey)
                    .build();
        }).collect(Collectors.toList());
        res.addAll(details);

        propsMap.entrySet().forEach(entry -> {
            if (!entry.getKey().equals(pk.getDatasourceId())) {
                //查询关联表的实体数据
                var tableMapping = tableFieldMappingMapper.selectBySourceAndTarget(
                        pk.getDatasourceSchema(), pk.getDatasourceId(), entry.getKey());
                if (tableMapping == null) {
                    return;
                }
                var orderBy = tableMetadataMapper.queryPrimaryKeyColumnName(pk.getDatasourceSchema(), entry.getKey());
                var columns = entry.getValue().stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList());
                var storageGroup = entry.getValue().get(0).getStorageGroup();
                var joinKey = datalakeKey(pk.getDatasourceSchema(), tableMapping.getTargetTableName(), tableMapping.getTargetColumnName(), entityPrimaryKey);
                var rowKeys = rowKeyList(rowKeyFor(rowKeysByGroup, storageGroup));
                final List<Map<String, Object>> otherData;
                if (rowKeys != null) {
                    // 指定了行就只取这些行：LIST 入参的多个位置按数组顺序取值，每个行主键都必须属于本实例
                    var rows = new ArrayList<Map<String, Object>>(rowKeys.size());
                    for (var specifiedKey : rowKeys) {
                        var rowKey = datalakeKey(pk.getDatasourceSchema(), tableMapping.getTargetTableName(), orderBy, specifiedKey);
                        var row = objectMapper.queryJoinTableRow(
                                pk.getDatasourceSchema(),
                                joinKey,
                                tableMapping.getTargetTableName(),
                                columns,
                                tableMapping.getTargetColumnName(),
                                orderBy,
                                rowKey
                        );
                        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(row),
                                "存储分组[" + storageGroup + "]中行主键 " + rowKey + " 不存在或不属于实体 "
                                        + entityPrimaryKey, HttpStatus.BAD_REQUEST);
                        // 缺失的列补成 null，与多行取值路径的输出形状保持一致
                        rows.addAll(objectMapper.populate(pk.getDatasourceSchema(), row,
                                tableMapping.getTargetTableName(), columns));
                    }
                    otherData = rows;
                } else {
                    var readRule = readRuleFor(listReadRules, storageGroup);
                    var ruleOrderColumn = readRule == null ? null
                            : ruleOrderColumn(entry.getValue(), readRule, storageGroup);
                    otherData = objectMapper.queryByJoinTable(
                            pk.getDatasourceSchema(),
                            joinKey,
                            tableMapping.getTargetTableName(),
                            columns,
                            tableMapping.getTargetColumnName(),
                            StringUtils.isNotEmpty(ruleOrderColumn) ? ruleOrderColumn : orderBy,
                            readRule != null && readRule.getRowCount() != null ? readRule.getRowCount()
                                    : groupRowLimit(entry.getValue(), maxRowsByProperty),
                            readRule != null && readRule.getSort() != null ? readRule.getSort().name()
                                    : QuerySortEnum.DESC.getValue()
                    );
                }

                if (CollectionUtils.isNotEmpty(otherData)) {
                    var otherColumns = otherData.get(0).keySet().stream().collect(Collectors.toList());
                    var otherPropertyMap = propsMap.get(entry.getKey()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
                    var detail = otherColumns.stream().<EntityPropertyDetailVO>map(col -> {
                        var p = otherPropertyMap.get(col);
                        var values = otherData.stream().map(v -> v.get(col)).collect(Collectors.toList());

                        return EntityPropertyDetailVO.builder()
                                .propertyDisplayName(p.getDisplayName())
                                .propertyUniqIdentifier(p.getUniqueIdentifier())
                                .propertyApiName(p.getApiName())
                                .propertyValues(values)
                                .entityPrimaryKey(entityPrimaryKey)
                                .categoryId(p.getPropertyCategoryId())
                                .metadata(p.getMetadata())
                                .build();
                    }).collect(Collectors.toList());
                    res.addAll(detail);
                }
            }
        });
        return res;
    }

    /**
     * 扩展分组本次读取的行数：一个分组的所有属性共用同一次多行查询，取该分组内被指定行数的属性中最大的一个；
     * 没指定行数按默认 10 行。
     */
    private int groupRowLimit(List<OntologyProperty> groupProps, Map<String, Integer> maxRowsByProperty) {
        if (MapUtils.isEmpty(maxRowsByProperty) || CollectionUtils.isEmpty(groupProps)) {
            return DEFAULT_GROUP_ROWS;
        }
        return groupProps.stream()
                .map(p -> maxRowsByProperty.get(p.getUniqueIdentifier()))
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(DEFAULT_GROUP_ROWS);
    }

    /** 该分组适用的读取规则：按入参建的读取视图只带该入参真正读到的分组 */
    private static ActionTestListReadRuleParam readRuleFor(Map<String, ActionTestListReadRuleParam> rules,
                                                           String storageGroup) {
        return MapUtils.isEmpty(rules) ? null : rules.get(storageGroup);
    }

    /**
     * 规则里的排序属性对应的列名：规则按入参挂到它绑定的子表分组上，该分组里找不到这个属性就说明规则不会生效，直接报错。
     */
    private String ruleOrderColumn(List<OntologyProperty> groupProps, ActionTestListReadRuleParam rule,
                                   String storageGroup) {
        if (StringUtils.isEmpty(rule.getOrderByProperty())) {
            return null;
        }
        var property = groupProps.stream()
                .filter(p -> rule.getOrderByProperty().equals(p.getApiName()))
                .findFirst().orElse(null);
        PreconditionUtils.checkArgument(property != null
                        && StringUtils.isNotEmpty(property.getDatasourceColumnName()),
                "子表读取规则按属性[" + rule.getOrderByProperty() + "]排序，但该属性不是存储分组[" + storageGroup
                        + "]下有关联数据源列的属性，请检查规则配的入参", HttpStatus.BAD_REQUEST);
        return property.getDatasourceColumnName();
    }

    /**
     * 某个入参在某对象上适用的读取规则：入参名 -> 对象id -> 存储分组 -> 规则，取本次这个对象的这一段。
     * <p>规则按子级字段归位到它所在的子表，同一入参下来自不同对象的子表各有各的规则；
     * 没被规则指到的分组和别的入参仍按默认口径读。</p>
     */
    private Map<String, ActionTestListReadRuleParam> readRulesForParam(ActionContextInfoDTO context,
                                                                        String paramName, String objectId) {
        var rulesByParam = context.getListReadRules();
        if (MapUtils.isEmpty(rulesByParam)) {
            return null;
        }
        var rulesByGroup = rulesByParam.get(paramName) == null ? null : rulesByParam.get(paramName).get(objectId);
        return MapUtils.isEmpty(rulesByGroup) ? null : rulesByGroup;
    }

    /**
     * 数据湖主键/行主键按列的实际类型对齐：行为树节点数据、前端文本框常把数字主键当字符串传，
     * PostgreSQL 不会把 varchar 参数隐式转成 integer/uuid，直接绑定会报「operator does not exist」。
     * <p>字符串列原样返回；值本身与列对不上时也原样返回，交给数据库报出准确原因。</p>
     */
    private Object datalakeKey(String schemaName, String tableName, String columnName, Object value) {
        if (!(value instanceof String text) || StringUtils.isEmpty(columnName)) {
            return value;
        }
        var trimmed = text.trim();
        if (StringUtils.isEmpty(trimmed)) {
            return value;
        }
        var type = datalakeColumnType(schemaName, tableName, columnName);
        if (StringUtils.isEmpty(type)) {
            return value;
        }
        var normalized = type.toLowerCase(Locale.ROOT);
        try {
            if (normalized.startsWith("integer") || normalized.equals("int4") || normalized.equals("serial")) {
                return new BigDecimal(trimmed).intValueExact();
            }
            if (normalized.startsWith("bigint") || normalized.equals("int8") || normalized.equals("bigserial")) {
                return new BigDecimal(trimmed).longValueExact();
            }
            if (normalized.startsWith("smallint") || normalized.equals("int2") || normalized.equals("smallserial")) {
                return new BigDecimal(trimmed).shortValueExact();
            }
            if (normalized.startsWith("numeric") || normalized.startsWith("decimal")) {
                return new BigDecimal(trimmed);
            }
            if (normalized.startsWith("double") || normalized.equals("float8")) {
                return Double.valueOf(trimmed);
            }
            if (normalized.startsWith("real") || normalized.equals("float4")) {
                return Float.valueOf(trimmed);
            }
            if (normalized.equals("uuid")) {
                return UUID.fromString(trimmed);
            }
            if (normalized.equals("boolean") || normalized.equals("bool")) {
                return switch (trimmed.toLowerCase(Locale.ROOT)) {
                    case "true", "t", "1", "yes" -> Boolean.TRUE;
                    case "false", "f", "0", "no" -> Boolean.FALSE;
                    default -> throw new IllegalArgumentException("不是布尔取值");
                };
            }
        } catch (RuntimeException e) {
            return value;
        }
        return value;
    }

    /** 列在库里的实际类型；表或列查不到时按原值走，不拦执行 */
    private String datalakeColumnType(String schemaName, String tableName, String columnName) {
        if (StringUtils.isEmpty(schemaName) || StringUtils.isEmpty(tableName)) {
            return null;
        }
        return tableMetadataMapper.queryColumnMetadata(schemaName, tableName).stream()
                .filter(column -> StringUtils.equals(columnName, column.getColumnName()))
                .map(TableColumnDesc::getType)
                .filter(StringUtils::isNotEmpty)
                .findFirst()
                .orElse(null);
    }

    @Override
    public EntityPropertyRowDetailVO getEntityPropertyRowDetail(EntityPropertyRowQueryParam param) {

        List<EntityPropertyRowDetailVO.PropertyGroup> propertyGroups = Lists.newArrayList();

        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyUniqueIdentifier()))
                .stream()
                .filter(v -> StringUtils.isNotEmpty(v.getDatasourceColumnName()))
                .collect(Collectors.toList());

        var primaryKeyProp = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        if (!primaryKeyProp.isPresent() || StringUtils.isEmpty(primaryKeyProp.get().getDatasourceColumnName())) {
            return EntityPropertyRowDetailVO.builder()
                    .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                    .entityPrimaryKey(param.getEntityPrimaryKey())
                    .build();
        }
        var propsMap = props.stream().collect(Collectors.groupingBy(v -> v.getDatasourceId()));
        //查询主属性表对应的实体数据
        var pk = primaryKeyProp.get();
        var pkColumns = propsMap.get(pk.getDatasourceId()).stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList());
        var entityPrimaryKeyValue = datalakeKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
        var primaryData = objectMapper.queryDataByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pkColumns, pk.getDatasourceColumnName(), entityPrimaryKeyValue);
        if (primaryData == null || primaryData.isEmpty()) {
            return EntityPropertyRowDetailVO.builder()
                    .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                    .entityPrimaryKey(param.getEntityPrimaryKey())
                    .propertyGroups(Lists.newArrayList())
                    .build();
        }
        var propertyMap = propsMap.get(pk.getDatasourceId()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        var propertyInfoList = primaryData.entrySet().stream().<EntityPropertyRowDetailVO.PropertyInfo>map(entry -> {
            var colName = entry.getKey();
            var colValue = entry.getValue();
            var p = propertyMap.get(colName);
            return EntityPropertyRowDetailVO.PropertyInfo.builder()
                    .propertyDisplayName(p.getDisplayName())
                    .propertyValue(colValue)
                    .propertyUniqIdentifier(p.getUniqueIdentifier())
                    .propertyApiName(p.getApiName())
                    .categoryId(p.getPropertyCategoryId())
                    .metadata(p.getMetadata())
                    .build();
        }).collect(Collectors.toList());
        propertyGroups.add(EntityPropertyRowDetailVO.PropertyGroup.builder()
                .storageGroup(pk.getStorageGroup())
                .groupDataList(Lists.newArrayList(EntityPropertyRowDetailVO.PropertyGroupData.builder()
                        .dataPrimaryKey(param.getEntityPrimaryKey())
                        .props(propertyInfoList)
                        .build()))
                .build());

        //查询关联属性表对应的实体数据
        propsMap.entrySet().forEach(entry -> {
            if (!entry.getKey().equals(pk.getDatasourceId())) {
                //查询关联表的实体数据
                var tableMapping = tableFieldMappingMapper.selectBySourceAndTarget(
                        pk.getDatasourceSchema(), pk.getDatasourceId(), entry.getKey());
                if (tableMapping == null) {
                    return;
                }
                var orderBy = tableMetadataMapper.queryPrimaryKeyColumnName(pk.getDatasourceSchema(), entry.getKey());
                var columns = entry.getValue().stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList());
                columns.add(orderBy);
                var joinKey = datalakeKey(pk.getDatasourceSchema(), tableMapping.getTargetTableName(), tableMapping.getTargetColumnName(), param.getEntityPrimaryKey());
                var otherData = objectMapper.queryByJoinTable(
                        pk.getDatasourceSchema(),
                        joinKey,
                        tableMapping.getTargetTableName(),
                        columns,
                        tableMapping.getTargetColumnName(),
                        orderBy,
                        100,
                        param.getSort().getValue()
                );

                if (CollectionUtils.isNotEmpty(otherData)) {
                    List<EntityPropertyRowDetailVO.PropertyGroupData> groupDataList = Lists.newArrayList();
                    var otherPropertyMap = propsMap.get(entry.getKey()).stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
                    for (var row : otherData) {
                        EntityPropertyRowDetailVO.PropertyGroupData groupData = new EntityPropertyRowDetailVO.PropertyGroupData();
                        List<EntityPropertyRowDetailVO.PropertyInfo> propertyInfos = Lists.newArrayList();
                        for (var col : row.keySet()) {
                            if (col.equals(orderBy)) {
                                var dataPk = row.get(col);
                                groupData.setDataPrimaryKey(dataPk);
                                continue;
                            }
                            var p = otherPropertyMap.get(col);
                            var info = EntityPropertyRowDetailVO.PropertyInfo.builder()
                                    .propertyDisplayName(p.getDisplayName())
                                    .propertyUniqIdentifier(p.getUniqueIdentifier())
                                    .propertyApiName(p.getApiName())
                                    .propertyValue(row.get(col))
                                    .categoryId(p.getPropertyCategoryId())
                                    .metadata(p.getMetadata())
                                    .build();
                            propertyInfos.add(info);
                        }
                        groupData.setProps(propertyInfos);
                        groupDataList.add(groupData);
                    }
                    propertyGroups.add(EntityPropertyRowDetailVO.PropertyGroup.builder()
                            .storageGroup(entry.getValue().get(0).getStorageGroup())
                            .groupDataList(groupDataList)
                            .build());
                }
            }
        });

        return EntityPropertyRowDetailVO.builder()
                .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                .entityPrimaryKey(param.getEntityPrimaryKey())
                .propertyGroups(propertyGroups)
                .build();
    }

    @Override
    public Page<EntityPropertyRowDetailVO.PropertyGroupData> getEntityPropertyRowDetailPage(EntityPropertyRowPageQueryParam param) {

        var pageNum = param.getPageNum();
        var pageSize = param.getPageSize();
        var offset = (pageNum - 1) * pageSize;

        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyUniqueIdentifier()))
                .stream()
                .filter(v -> StringUtils.isNotEmpty(v.getDatasourceColumnName()))
                .collect(Collectors.toList());
        var primaryKeyProp = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(primaryKeyProp.isPresent()
                        && StringUtils.isNotEmpty(primaryKeyProp.get().getDatasourceId())
                        && StringUtils.isNotEmpty(primaryKeyProp.get().getDatasourceColumnName()),
                "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = primaryKeyProp.get();
        var entityPrimaryKeyValue = datalakeKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
        var pkData = objectMapper.queryDataByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(),
                Lists.newArrayList(pk.getDatasourceColumnName()), pk.getDatasourceColumnName(), entityPrimaryKeyValue);
        PreconditionUtils.checkArgument(MapUtils.isNotEmpty(pkData), "实体" + param.getEntityPrimaryKey() + "不存在");

        //定位存储分组对应的数据源表，一个分组只能绑一张表，否则分页无唯一口径
        var storageGroup = param.getStorageGroup();
        var groupProps = props.stream()
                .filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(groupProps), "存储分组" + storageGroup + "不存在或未绑定数据源");
        var datasourceIds = groupProps.stream().map(OntologyProperty::getDatasourceId).distinct().collect(Collectors.toList());
        PreconditionUtils.checkArgument(datasourceIds.size() == 1, "存储分组" + storageGroup + "绑定了多个数据源，无法分页查询", HttpStatus.BAD_REQUEST);
        var ds = datasourceIds.get(0);
        var propertyMap = groupProps.stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));

        //主表分组：实例本身只有一行，按主键查询，分页只影响返回的 total
        if (ds.equals(pk.getDatasourceId())) {
            var row = objectMapper.queryDataByPrimaryKey(pk.getDatasourceSchema(), ds, Lists.newArrayList(propertyMap.keySet()),
                    pk.getDatasourceColumnName(), entityPrimaryKeyValue);
            var page = new Page<EntityPropertyRowDetailVO.PropertyGroupData>(pageNum, pageSize, row == null ? 0 : 1);
            if (row != null) {
                page.setRecords(Lists.newArrayList(buildRowGroupData(row, propertyMap, pk.getDatasourceColumnName(), param.getEntityPrimaryKey())));
            }
            return page;
        }

        //关联属性表：按实例主键过滤后分页
        var tableMapping = tableFieldMappingMapper.selectBySourceAndTarget(pk.getDatasourceSchema(), pk.getDatasourceId(), ds);
        PreconditionUtils.checkArgument(tableMapping != null, "存储分组" + storageGroup + "与主表未建立关联关系", HttpStatus.BAD_REQUEST);
        var orderBy = tableMetadataMapper.queryPrimaryKeyColumnName(pk.getDatasourceSchema(), ds);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(orderBy), "存储分组" + storageGroup + "的数据源表缺少主键列", HttpStatus.BAD_REQUEST);
        var columns = Lists.newArrayList(propertyMap.keySet());
        columns.add(orderBy);
        var joinKey = datalakeKey(pk.getDatasourceSchema(), tableMapping.getTargetTableName(), tableMapping.getTargetColumnName(), param.getEntityPrimaryKey());
        var total = objectMapper.queryJoinTableCount(pk.getDatasourceSchema(), joinKey,
                tableMapping.getTargetTableName(), tableMapping.getTargetColumnName());
        var rows = objectMapper.queryByJoinTablePage(pk.getDatasourceSchema(), joinKey,
                tableMapping.getTargetTableName(), columns, tableMapping.getTargetColumnName(), orderBy,
                pageSize, offset, param.getSort().getValue());
        var page = new Page<EntityPropertyRowDetailVO.PropertyGroupData>(pageNum, pageSize, total == null ? 0 : total);
        var records = Lists.<EntityPropertyRowDetailVO.PropertyGroupData>newArrayList();
        for (var row : rows) {
            records.add(buildRowGroupData(row, propertyMap, orderBy, null));
        }
        page.setRecords(records);
        return page;
    }

    @Override
    public List<EntityStorageGroupDataVO> queryByStorageGroup(List<EntityStorageGroupQueryParam> params) {
        if (CollectionUtils.isEmpty(params)) {
            return Lists.newArrayList();
        }
        return params.stream().map(this::queryStorageGroupRows).collect(Collectors.toList());
    }

    private EntityStorageGroupDataVO queryStorageGroupRows(EntityStorageGroupQueryParam param) {
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyUniqueIdentifier()))
                .stream()
                .filter(v -> StringUtils.isNotEmpty(v.getDatasourceColumnName()))
                .collect(Collectors.toList());
        var optionalPkProperty = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(optionalPkProperty.isPresent() && StringUtils.isNotEmpty(optionalPkProperty.get().getDatasourceId()),
                "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = optionalPkProperty.get();
        var schemaName = pk.getDatasourceSchema();
        var mainTableName = pk.getDatasourceId();

        //指定实例时先确认实体存在，否则查不到数据也分不清是 id 写错还是本来没有数据
        if (param.getEntityPrimaryKey() != null) {
            var entityKeyValue = datalakeKey(schemaName, mainTableName, pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
            var pkData = objectMapper.queryDataByPrimaryKey(schemaName, mainTableName,
                    Lists.newArrayList(pk.getDatasourceColumnName()), pk.getDatasourceColumnName(), entityKeyValue);
            PreconditionUtils.checkArgument(MapUtils.isNotEmpty(pkData), "实体" + param.getEntityPrimaryKey() + "不存在");
        }

        //一个分组只绑一张表，否则排序属性与所属实体都没有唯一口径
        var storageGroup = param.getStorageGroup();
        var groupProps = props.stream()
                .filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(groupProps), "存储分组" + storageGroup + "不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var groupTables = groupProps.stream().map(OntologyProperty::getDatasourceId).distinct().collect(Collectors.toList());
        PreconditionUtils.checkArgument(groupTables.size() == 1, "存储分组" + storageGroup + "绑定了多个数据源表，无法排序取数", HttpStatus.BAD_REQUEST);
        var groupTable = groupTables.get(0);
        var columnPropertyMap = groupProps.stream()
                .collect(Collectors.toMap(OntologyProperty::getDatasourceColumnName, v -> v, (a, b) -> a));

        //排序列只取分组内属性绑定的列，用户传的属性名不直接进 SQL
        var sortProperty = groupProps.stream()
                .filter(v -> StringUtils.equals(v.getApiName(), param.getSortBy()))
                .findFirst()
                .or(() -> groupProps.stream().filter(v -> StringUtils.equals(v.getDatasourceColumnName(), param.getSortBy())).findFirst())
                .or(() -> groupProps.stream().filter(v -> StringUtils.equals(v.getDisplayName(), param.getSortBy())).findFirst());
        PreconditionUtils.checkArgument(sortProperty.isPresent(),
                "存储分组" + storageGroup + "里没有排序属性[" + param.getSortBy() + "]", HttpStatus.BAD_REQUEST);
        var sortColumn = sortProperty.get().getDatasourceColumnName();

        //主分组的行本身就是实体；关联分组的行按回指主表的列定位所属实体
        var isMainGroup = groupTable.equals(mainTableName);
        String entityKeyColumn;
        if (isMainGroup) {
            entityKeyColumn = pk.getDatasourceColumnName();
        } else {
            var tableMapping = tableFieldMappingMapper.selectBySourceAndTarget(schemaName, mainTableName, groupTable);
            PreconditionUtils.checkArgument(tableMapping != null, "存储分组" + storageGroup + "与主表未建立关联关系", HttpStatus.BAD_REQUEST);
            entityKeyColumn = tableMapping.getTargetColumnName();
        }
        var titleColumn = props.stream()
                .filter(v -> StringUtils.equals(v.getDatasourceId(), mainTableName) && v.getIsTitleKey() != null && v.getIsTitleKey() == 1)
                .map(OntologyProperty::getDatasourceColumnName)
                .findFirst().orElse(null);

        var columns = Lists.newArrayList(columnPropertyMap.keySet());
        if (!columns.contains(entityKeyColumn)) {
            columns.add(entityKeyColumn);
        }
        //标题属性未必归在 main 分组里，主分组查询要显式带上它才能拿到展示名
        if (isMainGroup && titleColumn != null && !columns.contains(titleColumn)) {
            columns.add(titleColumn);
        }
        //指定实例：按分组表里表示所属实体的列过滤（主分组是主键列，关联分组是回指主表的列）
        String filterColumn = null;
        Object filterValue = null;
        if (param.getEntityPrimaryKey() != null) {
            filterColumn = entityKeyColumn;
            filterValue = datalakeKey(schemaName, groupTable, entityKeyColumn, param.getEntityPrimaryKey());
        }
        //属性过滤复用通用查询的过滤树；分组数据都在同一张表，过滤属性只认本分组的属性
        String whereClause = null;
        if (param.getFilters() != null && CollectionUtils.isNotEmpty(param.getFilters().getChildren())) {
            var groupPropByApiName = groupProps.stream()
                    .collect(Collectors.toMap(OntologyProperty::getApiName, v -> v, (a, b) -> a));
            collectFilterPropertyApiNames(param.getFilters()).forEach(apiName -> PreconditionUtils.checkArgument(
                    groupPropByApiName.containsKey(apiName),
                    "过滤属性" + apiName + "不属于存储分组" + storageGroup + "，分组过滤只能用本分组的属性", HttpStatus.BAD_REQUEST));
            validateFilterGroup(param.getFilters(), groupPropByApiName);
            whereClause = buildWhereClause(param.getFilters(), groupPropByApiName, schemaName);
        }
        var hasDeletedField = tableMetadataMapper.isColumnExist(schemaName, groupTable, "is_deleted");
        var rows = objectMapper.querySortedData(schemaName, groupTable, columns, filterColumn, filterValue, whereClause,
                sortColumn, param.sortOrDefault().getValue(), param.getLimit(), hasDeletedField);

        //关联分组的展示名不在本表里，按本次涉及到的实体主键一次性回捞主表
        var displayNames = Maps.<Object, String>newHashMap();
        if (!isMainGroup) {
            var entityKeyValues = rows.stream().map(row -> row.get(entityKeyColumn))
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            var mainColumns = Lists.newArrayList(pk.getDatasourceColumnName());
            if (titleColumn != null) {
                mainColumns.add(titleColumn);
            }
            if (CollectionUtils.isNotEmpty(entityKeyValues)) {
                objectMapper.queryByPrimaryKeyList(schemaName, mainTableName, mainColumns, pk.getDatasourceColumnName(), entityKeyValues)
                        .forEach(mainRow -> displayNames.put(mainRow.get(pk.getDatasourceColumnName()),
                                rowDisplayName(mainRow, titleColumn, pk.getDatasourceColumnName())));
            }
        }

        var records = rows.stream().map(row -> {
            var entityPrimaryKey = row.get(entityKeyColumn);
            return EntityStorageGroupDataVO.StorageGroupRow.builder()
                    .entityPrimaryKey(entityPrimaryKey)
                    .displayName(isMainGroup ? rowDisplayName(row, titleColumn, entityKeyColumn)
                            : displayNames.get(entityPrimaryKey))
                    .props(row.entrySet().stream()
                            .filter(entry -> columnPropertyMap.containsKey(entry.getKey()))
                            .map(entry -> buildPropertyInfo(columnPropertyMap.get(entry.getKey()), entry.getValue()))
                            .collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());

        return EntityStorageGroupDataVO.builder()
                .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                .storageGroup(storageGroup)
                .rows(records)
                .build();
    }

    /**
     * 实体在主存储分组里的展示名：标题属性没值时回落到实体主键，与实体列表接口的口径一致。
     */
    private String rowDisplayName(Map<String, Object> mainRow, String titleColumn, String primaryKeyColumn) {
        var title = titleColumn == null ? null : mainRow.get(titleColumn);
        var value = title == null ? mainRow.get(primaryKeyColumn) : title;
        return value == null ? null : String.valueOf(value);
    }

    private EntityPropertyRowDetailVO.PropertyInfo buildPropertyInfo(OntologyProperty property, Object value) {
        return EntityPropertyRowDetailVO.PropertyInfo.builder()
                .propertyDisplayName(property.getDisplayName())
                .propertyUniqIdentifier(property.getUniqueIdentifier())
                .propertyApiName(property.getApiName())
                .propertyValue(value)
                .categoryId(property.getPropertyCategoryId())
                .metadata(property.getMetadata())
                .build();
    }

    private EntityPropertyRowDetailVO.PropertyGroupData buildRowGroupData(Map<String, Object> row,
                                                                          Map<String, OntologyProperty> propertyMap,
                                                                          String dataPrimaryKeyColumn,
                                                                          Object entityPrimaryKey) {
        var groupData = new EntityPropertyRowDetailVO.PropertyGroupData();
        var propertyInfos = Lists.<EntityPropertyRowDetailVO.PropertyInfo>newArrayList();
        for (var entry : row.entrySet()) {
            var col = entry.getKey();
            if (col.equals(dataPrimaryKeyColumn)) {
                groupData.setDataPrimaryKey(entityPrimaryKey == null ? entry.getValue() : entityPrimaryKey);
                continue;
            }
            var p = propertyMap.get(col);
            if (p == null) {
                continue;
            }
            propertyInfos.add(EntityPropertyRowDetailVO.PropertyInfo.builder()
                    .propertyDisplayName(p.getDisplayName())
                    .propertyUniqIdentifier(p.getUniqueIdentifier())
                    .propertyApiName(p.getApiName())
                    .propertyValue(entry.getValue())
                    .categoryId(p.getPropertyCategoryId())
                    .metadata(p.getMetadata())
                    .build());
        }
        groupData.setProps(propertyInfos);
        return groupData;
    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public List<Object> createEntities(EntityCreateParam param) {
        var ontologyIdentifier = param.getOntologyIdentifier();
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var optionalPkProperty = ontologyProperties.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(optionalPkProperty.isPresent() && StringUtils.isNotEmpty(optionalPkProperty.get().getDatasourceId()), "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = optionalPkProperty.get();
        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTable(pk.getDatasourceSchema(), pk.getDatasourceId());

        var ontologyPropertyMap = ontologyProperties.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v));
        List<Object> entityPrimaryKeys = Lists.newArrayList();
        //插入实体
        param.getEntityList().forEach(entity -> {
            var entityGroupProperties = entity.getEntityProperties();
            var mainStorage = entityGroupProperties.stream().filter(v -> v.getStorageGroup().equals("main")).findFirst().get();
            //插入实体主属性
            var mainProps = mainStorage.getProps().get(0);
            var columnValues = mainProps.stream().collect(Collectors.toMap(v -> {
                        String columnName = ontologyPropertyMap.get(v.getPropertyApiName()).getDatasourceColumnName();
                        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(columnName), v.getPropertyApiName() + "未关联数据源");
                        return columnName;
                    },
                    v -> v.getPropertyValue()));
            Object entityPK = objectMapper.batchInsertObjectReturnKey(pk.getDatasourceSchema(), pk.getDatasourceId(), Lists.newArrayList(columnValues), pk.getDatasourceColumnName()).get(0);
            entityPrimaryKeys.add(entityPK);
            //插入实体关联属性
            var entityProperties = entityGroupProperties.stream().filter(v -> !v.getStorageGroup().equals("main")).collect(Collectors.toList());
            entityProperties.stream().forEach(entityProperty -> {
                var storageGroup = entityProperty.getStorageGroup();
                var ontologyPropertyList = ontologyProperties.stream().filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                        .collect(Collectors.toList());
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ontologyPropertyList), "存储分组" + storageGroup + "未绑定数据源");
                var ds = ontologyPropertyList.get(0).getDatasourceId();
                var tableFieldMapping = tableFieldMappings.stream().filter(v -> v.getTargetTableName().equals(ds)).findFirst().get();

                List<Map<String, Object>> otherPropertyColumnValues = Lists.newArrayList();

                entityProperty.getProps().forEach(props -> {
                    Map<String, Object> columnsMap = new HashMap<>();
                    ontologyPropertyList.forEach(prop -> columnsMap.put(prop.getDatasourceColumnName(), null));
                    props.stream().forEach(v -> {
                        var columnName = ontologyPropertyMap.get(v.getPropertyApiName()).getDatasourceColumnName();
                        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(columnName), v.getPropertyApiName() + "未关联数据源");
                        columnsMap.put(columnName, v.getPropertyValue());
                    });
                    columnsMap.put(tableFieldMapping.getTargetColumnName(), entityPK);
                    otherPropertyColumnValues.add(columnsMap);
                });
                objectMapper.batchInsertObject(pk.getDatasourceSchema(), ds, otherPropertyColumnValues);
            });
        });
        //todo 创建实体节点和关系
        return entityPrimaryKeys;
    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public void insertEntityProperty(EntityPropertyInsertParam param) {

        var ontologyIdentifier = param.getOntologyIdentifier();
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var optionalPkProperty = ontologyProperties.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(optionalPkProperty.isPresent() && StringUtils.isNotEmpty(optionalPkProperty.get().getDatasourceId()), "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = optionalPkProperty.get();
        var entityPrimaryKeyValue = datalakeKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
        //校验实体id是否存在
        var pkData = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), Lists.newArrayList(pk.getDatasourceColumnName()), pk.getDatasourceColumnName(), entityPrimaryKeyValue);
        PreconditionUtils.checkArgument(MapUtils.isNotEmpty(pkData), "实体" + param.getEntityPrimaryKey() + "不存在");

        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTable(pk.getDatasourceSchema(), pk.getDatasourceId());
        var ontologyPropertyMap = ontologyProperties.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v));

        //插入实体关联属性
        var entityProperties = param.getEntityProperties().stream()
                .filter(v -> !v.getStorageGroup().equals("main"))
                .collect(Collectors.toList());

        entityProperties.stream().forEach(entityProperty -> {
            var storageGroup = entityProperty.getStorageGroup();
            var ontologyPropertyList = ontologyProperties.stream().filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                    .collect(Collectors.toList());
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ontologyPropertyList), "存储分组" + storageGroup + "未绑定数据源");
            var ds = ontologyPropertyList.get(0).getDatasourceId();
            var tableFieldMapping = tableFieldMappings.stream().filter(v -> v.getTargetTableName().equals(ds)).findFirst().get();

            List<Map<String, Object>> otherPropertyColumnValues = Lists.newArrayList();

            entityProperty.getProps().forEach(props -> {
                Map<String, Object> columnsMap = new HashMap<>();
                ontologyPropertyList.forEach(prop -> columnsMap.put(prop.getDatasourceColumnName(), null));
                props.stream().forEach(v -> {
                    var columnName = ontologyPropertyMap.get(v.getPropertyApiName()).getDatasourceColumnName();
                    PreconditionUtils.checkArgument(StringUtils.isNotEmpty(columnName), v.getPropertyApiName() + "未关联数据源");
                    columnsMap.put(columnName, v.getPropertyValue());
                });
                columnsMap.put(tableFieldMapping.getTargetColumnName(), entityPrimaryKeyValue);
                otherPropertyColumnValues.add(columnsMap);
            });
            objectMapper.batchInsertObject(pk.getDatasourceSchema(), ds, otherPropertyColumnValues);
        });

    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public void updateEntityProperty(EntityPropertyUpdateParam param) {
        //获取对象、属性、主键信息
        var ontologyIdentifier = param.getOntologyIdentifier();
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var optionalPkProperty = ontologyProperties.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(optionalPkProperty.isPresent() && StringUtils.isNotEmpty(optionalPkProperty.get().getDatasourceId()), "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = optionalPkProperty.get();
        var entityPrimaryKeyValue = datalakeKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
        //校验实体id是否存在
        var pkData = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), Lists.newArrayList(pk.getDatasourceColumnName()), pk.getDatasourceColumnName(), entityPrimaryKeyValue);
        PreconditionUtils.checkArgument(MapUtils.isNotEmpty(pkData), "实体" + param.getEntityPrimaryKey() + "不存在");
        var ontologyPropertyMap = ontologyProperties.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v));
        // 校验存储分组
        var storageGroup = param.getStorageGroup();
        var ontologyPropertyList = ontologyProperties.stream().filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ontologyPropertyList), "存储分组" + storageGroup + "未绑定数据源");
        var ds = ontologyPropertyList.get(0).getDatasourceId();
        var primaryKeyColumnName = tableMetadataMapper.queryPrimaryKeyColumnName(pk.getDatasourceSchema(), ds);
        var dataPrimaryKeyValue = datalakeKey(pk.getDatasourceSchema(), ds, primaryKeyColumnName, param.getDataPrimaryKey());
        // 校验dataPrimaryKey
        if (storageGroup.equals("main")) {
            PreconditionUtils.checkArgument(Objects.equals(entityPrimaryKeyValue, dataPrimaryKeyValue), "属性数据主键和实体主键不一致");
        } else {
            var tableFieldMapping = tableFieldMappingMapper.selectBySourceAndTarget(
                    pk.getDatasourceSchema(), pk.getDatasourceId(), ds);
            var existDataMap = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), ds, Lists.newArrayList(), primaryKeyColumnName, dataPrimaryKeyValue);
            if (MapUtils.isEmpty(existDataMap)) {
                return;
            }
            PreconditionUtils.checkArgument(existDataMap.get(tableFieldMapping.getTargetColumnName()).equals(entityPrimaryKeyValue), "无权限修改其他实体的属性数据", HttpStatus.FORBIDDEN);
        }

        //生成属性更新map
        Map<String, Object> columnsMap = new HashMap<>();
        param.getProps().stream().forEach(v -> {
            PreconditionUtils.checkArgument(!v.getPropertyApiName().equals(pk.getApiName()), "属性主键不能修改");
            var columnName = ontologyPropertyMap.get(v.getPropertyApiName()).getDatasourceColumnName();
            PreconditionUtils.checkArgument(StringUtils.isNotEmpty(columnName), v.getPropertyApiName() + "未关联数据源");
            columnsMap.put(columnName, v.getPropertyValue());
        });
        objectMapper.updateObject(pk.getDatasourceSchema(), ds, columnsMap, primaryKeyColumnName, dataPrimaryKeyValue);
    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public void deleteEntityProperty(EntityPropertyDeleteParam param) {
        //获取对象、属性、主键信息
        var ontologyIdentifier = param.getOntologyIdentifier();
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var optionalPkProperty = ontologyProperties.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst();
        PreconditionUtils.checkArgument(optionalPkProperty.isPresent() && StringUtils.isNotEmpty(optionalPkProperty.get().getDatasourceId()), "主键不存在或未绑定数据源", HttpStatus.BAD_REQUEST);
        var pk = optionalPkProperty.get();
        var entityPrimaryKeyValue = datalakeKey(pk.getDatasourceSchema(), pk.getDatasourceId(), pk.getDatasourceColumnName(), param.getEntityPrimaryKey());
        //校验实体id是否存在
        var pkData = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), pk.getDatasourceId(), Lists.newArrayList(pk.getDatasourceColumnName()), pk.getDatasourceColumnName(), entityPrimaryKeyValue);
        PreconditionUtils.checkArgument(MapUtils.isNotEmpty(pkData), "实体" + param.getEntityPrimaryKey() + "不存在");
        // 校验存储分组
        var storageGroup = param.getStorageGroup();
        var ontologyPropertyList = ontologyProperties.stream().filter(v -> StringUtils.equals(storageGroup, v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(ontologyPropertyList), "存储分组" + storageGroup + "未绑定数据源");
        var ds = ontologyPropertyList.get(0).getDatasourceId();
        var primaryKeyColumnName = tableMetadataMapper.queryPrimaryKeyColumnName(pk.getDatasourceSchema(), ds);
        var dataPrimaryKeyValue = datalakeKey(pk.getDatasourceSchema(), ds, primaryKeyColumnName, param.getDataPrimaryKey());
        // 校验dataPrimaryKey
        if (storageGroup.equals("main")) {
            PreconditionUtils.checkArgument(Objects.equals(entityPrimaryKeyValue, dataPrimaryKeyValue), "属性数据主键和实体主键不一致");
            //删除实体和所有关联属性
            objectMapper.deleteByTableNameAndColumn(pk.getDatasourceSchema(), ds, primaryKeyColumnName, dataPrimaryKeyValue);
            var tableFieldMappingList = tableFieldMappingMapper.selectBySourceTable(pk.getDatasourceSchema(), ds);
            tableFieldMappingList.forEach(
                    tableFieldMapping -> objectMapper.deleteByTableNameAndColumn(
                            pk.getDatasourceSchema(),
                            tableFieldMapping.getTargetTableName(),
                            tableFieldMapping.getTargetColumnName(),
                            dataPrimaryKeyValue)
            );
            //todo 删除实体节点和关系

        } else {
            var tableFieldMapping = tableFieldMappingMapper.selectBySourceAndTarget(
                    pk.getDatasourceSchema(), pk.getDatasourceId(), ds);
            var existDataMap = objectMapper.queryByPrimaryKey(pk.getDatasourceSchema(), ds, Lists.newArrayList(), primaryKeyColumnName, dataPrimaryKeyValue);
            if (MapUtils.isEmpty(existDataMap)) {
                return;
            }
            PreconditionUtils.checkArgument(existDataMap.get(tableFieldMapping.getTargetColumnName()).equals(entityPrimaryKeyValue), "无权限删除其他实体的属性数据", HttpStatus.FORBIDDEN);
            //删除单个属性
            objectMapper.deleteByTableNameAndColumn(pk.getDatasourceSchema(), ds, primaryKeyColumnName, dataPrimaryKeyValue);
        }
    }

    @Override
    public Integer countEntity(String datasourceSchema, String datasourceId) {
        return objectMapper.count(datasourceSchema, datasourceId);
    }

    @Override
    public void updateEntityRelation(EntityRelationUpdateParam param) {
        var linkId = param.getLinkUniqIdentifier();
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, linkId));
        PreconditionUtils.checkNotNull(link, "invalid link uniqIdentifier", HttpStatus.BAD_REQUEST);
        var relation = relationRepository.queryRelationByFromNodeAndToNode(
                link.getOntologyUniqueIdentifierFrom(),
                param.getEntityPrimaryKeyFrom(),
                link.getOntologyUniqueIdentifierTo(),
                param.getEntityPrimaryKeyTo(),
                linkId);

        //更新实体关系（已存在）
        if (relation != null) {
            var visibilityWindows = param.getVisibilityWindows();
            var status = param.getStatus();
            // 行为定义的「修改」只改填了的那几项，没填的保留边上原值，避免改状态顺手把已有时段抹掉
            var partialUpdate = Boolean.TRUE.equals(param.getPartialUpdate());
            var keepWindows = partialUpdate && CollectionUtils.isEmpty(visibilityWindows);
            if (partialUpdate) {
                if (keepWindows) {
                    visibilityWindows = relation.getTimeWindows();
                }
                if (status == null) {
                    status = relation.getStatus();
                }
            }
            // 一级起止时间是「最近的一个存续窗口」，数组存全部窗口；没改时段就保留边上原值
            Date startTime;
            Date endTime;
            if (keepWindows) {
                startTime = relation.getStartTime();
                endTime = relation.getEndTime();
            } else {
                var nearest = nearestWindow(visibilityWindows);
                startTime = nearest == null ? null : nearest.getStartTime();
                endTime = nearest == null ? null : nearest.getEndTime();
            }
            relationRepository.updateRelation(startTime, endTime, visibilityWindows, status, relation.getId());
        }
        //插入新的实体关系：图库缺端点节点时先补节点，建边不再依赖调用方预先同步
        else {
            var fromNode = findOrCreateEntityNode(link.getOntologyUniqueIdentifierFrom(), param.getEntityPrimaryKeyFrom());
            var toNode = findOrCreateEntityNode(link.getOntologyUniqueIdentifierTo(), param.getEntityPrimaryKeyTo());
            PreconditionUtils.checkArgument(fromNode != null && toNode != null, "关系端点实体主键不能为空", HttpStatus.BAD_REQUEST);
            var nearest = nearestWindow(param.getVisibilityWindows());
            relation = EntityRelation.builder()
                    .ontologyLinkId(link.getUniqueIdentifier())
                    .from(fromNode)
                    .to(toNode)
                    .status(param.getStatus())
                    .startTime(nearest == null ? null : nearest.getStartTime())
                    .endTime(nearest == null ? null : nearest.getEndTime())
                    .timeWindows(param.getVisibilityWindows())
                    .createTime(new Date())
                    .updateTime(new Date())
                    .type(link.getType())
                    .name(link.getName())
                    .build();
            relationRepository.save(relation);
        }
    }

    /**
     * 取「最近的一个存续窗口」：未结束的窗口里开始最早的那个（正在生效的窗口自然排第一）；
     * 全部窗口都已结束时取最晚结束的那个。
     * <p>
     * 图库边上的一级 startTime/endTime 用它，便于按单个时间区间过滤；
     * 完整的多组时段仍留在 timeWindows 数组里，数组含被提到一级字段的那一段。
     */
    private VisibilityWindow nearestWindow(List<VisibilityWindow> windows) {
        if (CollectionUtils.isEmpty(windows)) {
            return null;
        }
        var now = new Date();
        var nearest = windows.stream()
                .filter(w -> w.getStartTime() != null && w.getEndTime() != null && !w.getEndTime().before(now))
                .min(Comparator.comparing(VisibilityWindow::getStartTime))
                .orElse(null);
        if (nearest != null) {
            return nearest;
        }
        return windows.stream().filter(w -> w.getEndTime() != null)
                .max(Comparator.comparing(VisibilityWindow::getEndTime))
                .orElse(null);
    }


    /**
     * 删除一条实例关系：按两端实体主键定位边并从图库物理删除。
     * <p>
     * 幂等：关系定义有效但边已不存在时直接返回，重复删除不报错。
     */
    public void deleteEntityRelation(String linkUniqIdentifier, Object entityPrimaryKeyFrom, Object entityPrimaryKeyTo) {
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkUniqIdentifier));
        PreconditionUtils.checkNotNull(link, "invalid link uniqIdentifier", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(entityPrimaryKeyFrom != null && entityPrimaryKeyTo != null,
                "关系端点实体主键不能为空", HttpStatus.BAD_REQUEST);
        var relation = relationRepository.queryRelationByFromNodeAndToNode(link.getOntologyUniqueIdentifierFrom(),
                normalizePrimaryKey(entityPrimaryKeyFrom), link.getOntologyUniqueIdentifierTo(),
                normalizePrimaryKey(entityPrimaryKeyTo), linkUniqIdentifier);
        if (relation == null) {
            return;
        }
        // deleteByIds 过滤的是 _id，取 getId()（映射 _key）会一条都删不掉
        relationRepository.deleteByIds(Lists.newArrayList(relation.getArangoId()));
    }


    @Override
    public Page<EntityInfoVO> getEntities(String ontologyUniqueIdentifier,
                                          String propertyName,
                                          Object propertyValue,
                                          Integer pageNum,
                                          Integer pageSize,
                                          Boolean needFilterVisibility) {
        var ontologyMeta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier));
        Page<EntityInfoVO> result = new Page<EntityInfoVO>().setSize(pageSize).setCurrent(pageNum);
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        if (CollectionUtils.isEmpty(props)) {
            return result;
        }
        var primaryProperty = props.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst().orElse(null);
        //主键数据源未绑定
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceColumnName())) {
            return result;
        }
        var primaryDatasource = primaryProperty.getDatasourceId();
        var schemaName = primaryProperty.getDatasourceSchema();
        Map<String, OntologyProperty> primaryPropMap;
        if (needFilterVisibility) {
            primaryPropMap = props.stream().filter(v -> StringUtils.equals(v.getDatasourceId(), primaryDatasource) && v.getVisibility() == 1)
                    .collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        } else {
            primaryPropMap = props.stream().filter(v -> StringUtils.equals(v.getDatasourceId(), primaryDatasource))
                    .collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v));
        }
        var titleKey = primaryPropMap.values().stream().filter(v -> v.getIsTitleKey() == 1).findFirst();

        String columnName = null;
        if (StringUtils.isNotEmpty(propertyName) && propertyValue != null) {
            var property = props.stream().filter(v -> v.getDisplayName().equals(propertyName)).findFirst();
            PreconditionUtils.checkArgument(property.isPresent() && StringUtils.isNotEmpty(property.get().getDatasourceColumnName()), "属性名称不存在或没有关联数据源：" + propertyName, HttpStatus.BAD_REQUEST);
            columnName = property.get().getDatasourceColumnName();
            var propertyType = property.get().getPropertyType();
            propertyValue = OntologyDataTypeEnum.convert(propertyType, propertyValue);
        }
        //分页查询实体数据
        var hasDeletedField = tableMetadataMapper.isColumnExist(schemaName, primaryDatasource, "is_deleted");
        var records = objectMapper.pageQuery(
                schemaName,
                primaryDatasource,
                primaryPropMap.values().stream().map(v -> v.getDatasourceColumnName()).collect(Collectors.toList()),
                columnName,
                propertyValue,
                pageSize,
                (pageNum - 1) * pageSize,
                hasDeletedField
        );
        var total = objectMapper.queryCount(schemaName, primaryDatasource, columnName, propertyValue, hasDeletedField);

        var entityRecords = records.stream().map(r -> {
            var entityPK = r.entrySet().stream()
                    .filter(v -> v.getKey().equals(primaryProperty.getDatasourceColumnName()))
                    .findFirst().get().getValue();

            var title = "";
            if (titleKey.isPresent()) {
                title = r.entrySet().stream()
                        .filter(v -> v.getKey().equals(titleKey.get().getDatasourceColumnName()))
                        .findFirst().get()
                        .getValue().toString();
            }

            var entityProps = r.entrySet().stream().map(entry -> {
                return EntityPropertyVO.builder()
                        .propertyApiName(primaryPropMap.get(entry.getKey()).getApiName())
                        .propertyDisplayName(primaryPropMap.get(entry.getKey()).getDisplayName())
                        .propertyValue(entry.getValue())
                        .build();
            }).collect(Collectors.toList());
            return EntityInfoVO.builder()
                    .properties(entityProps)
                    .displayName(StringUtils.isEmpty(title) ? entityPK.toString() : title)
                    .primaryKey(entityPK)
                    .ontologyUniqueIdentifier(ontologyUniqueIdentifier)
                    .ontologyName(ontologyMeta.getDisplayName())
                    .build();
        }).collect(Collectors.toList());

        return result.setTotal(total)
                .setRecords(entityRecords);
    }

    @Override
    public List<EntityNode> getByByOntologyUniqIdentifier(String ontologyIdentifier) {
        return nodeRepository.findByOntologyUniqIdentifier(ontologyIdentifier);
    }

    @Override
    public String executeAction(EntityActionExecuteParam param) throws Exception {
        // 按对象方向指定实例/行是内部载体（单行为测试给行为树用），对外执行请求不接受，传了也只是忽略
        param.setInputEntities(null);
        param.setOutputEntities(null);
        // LIST 入参按元素给位置同样只来自行为测试的请求体，对外执行请求走 inputParams 整包写法
        param.setInputElementParams(null);
        // 对外的单实体执行属于手动触发，一次请求记一条手动执行日志
        return manualExecuteLogService.runAndLog(param.getOntologySpaceId(), param.getActionApi(), param,
                () -> runEntityAction(param), msg -> msg);
    }

    @SneakyThrows
    private String runEntityAction(EntityActionExecuteParam param) {
        var infoDTO = initActionContextInfoDTO(param);
        //获取行为关联关系下的对象的实体详情
        List<List<EntityPropertyDetailVO>> linkedEntities = getLinkedEntities(infoDTO);
        return executeEntityAction(param, infoDTO, linkedEntities);
    }

    public List<List<EntityPropertyDetailVO>> getLinkedEntities(ActionContextInfoDTO infoDTO) {
        // 不绑算子的行为只动一行属性或一条指定的边，遍历对端全部实例既无意义也昂贵
        if (infoDTO.getFunctionDetailVO() == null) {
            return null;
        }
        if (infoDTO.getLink() != null) {
            //如果行为影响关系，找到关系另一端的对象
            var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getUniqueIdentifier, infoDTO.getLink().getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + infoDTO.getLink().getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
            var linkedOntology = counterpartOntology(linkGroup, infoDTO.getEntityActionExecuteParam().getOntologyUniqueIdentifier());
            infoDTO.setLinkToOntologyUniqueIdentifier(linkedOntology);
            //如果已指定单个实体：优先用关系对端专用参数，其次取各对象实例主键 map 中对端的那个
            var param = infoDTO.getEntityActionExecuteParam();
            checkDirectionalEntitiesNotEndpoints(linkGroup, param);
            var linkedPrimaryKey = param.getLinkedEntityPrimaryKey();
            if (linkedPrimaryKey == null && MapUtils.isNotEmpty(param.getEntityPrimaryKeys())) {
                linkedPrimaryKey = param.getEntityPrimaryKeys().get(linkedOntology);
            }
            Page<EntityInfoVO> entities;
            if (StringUtils.isNotEmpty(param.getLinkedOntologyUniqueIdentifier())
                    && param.getLinkedEntityPrimaryKey() != null) {
                PreconditionUtils.checkArgument(StringUtils.equals(linkedOntology, param.getLinkedOntologyUniqueIdentifier()), "关联的对象id不一致");
            }
            if (linkedPrimaryKey != null) {
                var primaryProp = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getIsPrimaryKey, 1)
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, linkedOntology));
                entities = getEntities(linkedOntology, primaryProp.getDisplayName(), linkedPrimaryKey, 1, Integer.MAX_VALUE, false);
            } else {
                //否则获取关联对象下的所有实体详情
                entities = getEntities(linkedOntology, "", "", 1, Integer.MAX_VALUE, false);
            }
            var records = entities.getRecords();
            var linkedEntities = records.stream().map(entity -> getEntityDetail(linkedOntology, entity.getPrimaryKey()))
                    .collect(Collectors.toList());

            return linkedEntities;
        }
        return null;
    }


    /**
     * 关系两端的实例由两端选择（测试的 from/to，或本端 + 对端参数）决定；按方向再指定一次会和两端选择打架，
     * 直接报错而不是静默忽略。只按方向给分组行主键不受影响（对端出参按行写回照旧生效）。
     */
    private void checkDirectionalEntitiesNotEndpoints(OntologyLinkGroup linkGroup, EntityActionExecuteParam param) {
        var endpoints = Set.of(linkGroup.getOntologyUniqueIdentifierFrom(), linkGroup.getOntologyUniqueIdentifierTo());
        var conflict = Stream.of(param.getInputEntities(), param.getOutputEntities())
                .filter(MapUtils::isNotEmpty)
                .flatMap(targets -> targets.entrySet().stream())
                .filter(entry -> entry.getValue() != null && entry.getValue().getEntityPrimaryKey() != null)
                .map(Map.Entry::getKey)
                .filter(endpoints::contains)
                .findFirst()
                .orElse(null);
        if (conflict != null) {
            throw new BusinessException("对象[" + conflict + "]是行为绑定关系的一端，实例主键请按关系两端传（from、to），"
                    + "不能在 inputEntities、outputEntities 里再指定 entityPrimaryKey", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 关系的对端对象：行为挂在 From 侧时对端是 To，挂在 To 侧时对端是 From。
     * 判断不出挂在哪一侧时沿用 To，与改造前的取值一致。
     */
    public String counterpartOntology(OntologyLinkGroup linkGroup, String selfOntology) {
        if (StringUtils.isNotEmpty(selfOntology)
                && StringUtils.equals(selfOntology, linkGroup.getOntologyUniqueIdentifierTo())) {
            return linkGroup.getOntologyUniqueIdentifierFrom();
        }
        return linkGroup.getOntologyUniqueIdentifierTo();
    }


    public ActionContextInfoDTO initActionContextInfoDTO(EntityActionExecuteParam param) {

        var actionDetailVO = actionService.getActionByApi(param.getOntologySpaceId(), param.getActionApi());
        // 行为树与行为同表，但它不绑算子也不写单条关系，按单个行为执行会报"算子不存在"这种误导提示
        PreconditionUtils.checkArgument(actionDetailVO.getActionKind() != ActionKindEnum.TREE,
                "行为树不能作为单个行为执行：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        // 属性值/实例关系行为只对单个实例改一行属性或一条边，读写天然同一个实例，按方向拆实例主键表达不出差别
        if (actionDetailVO.getActionKind() == ActionKindEnum.BASE
                && (ActionInstanceTargetParam.hasEntityPrimaryKey(param.getInputEntities())
                || ActionInstanceTargetParam.hasEntityPrimaryKey(param.getOutputEntities()))) {
            throw new BusinessException("属性值/实例关系行为只操作单个实例，"
                    + "不支持用 inputEntities、outputEntities 的 entityPrimaryKey 按方向指定实例",
                    HttpStatus.BAD_REQUEST);
        }
        // 属性值、实例关系行为不绑算子，上下文里没有算子定义，执行时按行为定义直接落库；
        // 算子 api 跨空间允许重名，取算子定义必须按行为所属空间，否则会跑到其他空间的同名算子上
        var functionDetailVO = StringUtils.isEmpty(actionDetailVO.getFunctionApi()) ? null
                : functionService.getFunctionDetailByApi(actionDetailVO.getOntologySpaceId(),
                actionDetailVO.getFunctionApi(), null);
        var link = actionDetailVO.getLinkMapping();
        var mappings = actionDetailVO.getMappingIns();
        //src对象属性
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyUniqueIdentifier()));
        // 应用行为的入参/出参可能指向其他对象：按映射收集涉及对象，补全各自属性与实例主键
        var entityPrimaryKeys = resolveEntityPrimaryKeys(param);
        checkDirectionalEntities(actionDetailVO, param);
        checkParamTargets(actionDetailVO, functionDetailVO, param);
        var objectIds = collectMappingObjectIds(actionDetailVO);
        if (StringUtils.isNotEmpty(param.getOntologyUniqueIdentifier())) {
            objectIds.add(param.getOntologyUniqueIdentifier());
        }
        objectIds.addAll(entityPrimaryKeys.keySet());
        var objectProperties = objectIds.isEmpty() ? new LinkedHashMap<String, List<OntologyProperty>>()
                : propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                                .in(OntologyProperty::getOntologyUniqueIdentifier, objectIds))
                        .stream().collect(Collectors.groupingBy(OntologyProperty::getOntologyUniqueIdentifier));

        //构造函数的输入参数
        var functionInputParams = functionDetailVO == null ? Lists.<FunctionParameterVO>newArrayList()
                : functionDetailVO.getParams().stream()
                .filter(v -> v.getCategory().equals(FunctionParamCategoryEnum.INPUT))
                .sorted(Comparator.comparing(v -> v.getParamOrder()))
                .collect(Collectors.toList());

        ActionContextInfoDTO infoDTO = ActionContextInfoDTO
                .builder()
                .actionDetailVO(actionDetailVO)
                .functionDetailVO(functionDetailVO)
                .link(link)
                .mappings(mappings)
                .ontologyProperties(ontologyProperties)
                .entityPrimaryKeys(entityPrimaryKeys)
                .objectProperties(objectProperties)
                .functionInputParams(functionInputParams)
                .entityActionExecuteParam(param)
                .listReadRules(param.getListReadRules())
                .build();

        return infoDTO;

    }

    /** 合并显式传入的各对象实例主键与单对象简写，显式 map 优先 */
    private Map<String, Object> resolveEntityPrimaryKeys(EntityActionExecuteParam param) {
        Map<String, Object> keys = new LinkedHashMap<>();
        if (MapUtils.isNotEmpty(param.getEntityPrimaryKeys())) {
            keys.putAll(param.getEntityPrimaryKeys());
        }
        if (StringUtils.isNotEmpty(param.getOntologyUniqueIdentifier()) && param.getEntityPrimaryKey() != null) {
            keys.putIfAbsent(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey());
        }
        if (StringUtils.isNotEmpty(param.getLinkedOntologyUniqueIdentifier()) && param.getLinkedEntityPrimaryKey() != null) {
            keys.putIfAbsent(param.getLinkedOntologyUniqueIdentifier(), param.getLinkedEntityPrimaryKey());
        }
        return keys;
    }

    /**
     * 按方向指定的**实例主键**必须落在真的会被读/写的对象上：对象 id 写错时静默回退到共用实例选择，
     * 会把数据写到没打算动的实例上，直接报错。只给分组行主键的对象维持原口径（行主键在读写时各自校验）。
     */
    private void checkDirectionalEntities(OntologyActionDetailVO detail, EntityActionExecuteParam param) {
        checkInstanceTargets(detail, param.getInputEntities(), true, "inputEntities", "入参读取");
        checkInstanceTargets(detail, param.getOutputEntities(), false, "outputEntities", "出参写回");
    }

    private void checkInstanceTargets(OntologyActionDetailVO detail, Map<String, ActionInstanceTargetParam> targets,
                                      Boolean inputOnly, String fieldName, String direction) {
        if (MapUtils.isEmpty(targets)) {
            return;
        }
        var objectIds = collectMappingObjectIds(detail, inputOnly);
        // 老数据的映射行没写对象id，收集不到涉及对象，此时不做归属校验，保持原有取值口径
        if (objectIds.isEmpty() && legacyMappingNeedsInstance(detail)) {
            return;
        }
        targets.forEach((objectId, target) -> {
            PreconditionUtils.checkArgument(target != null
                            && (target.getEntityPrimaryKey() != null || MapUtils.isNotEmpty(target.getDataPrimaryKeys())),
                    fieldName + " 里的对象[" + objectId + "]没给实例主键，也没给分组行主键", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(target.getEntityPrimaryKey() == null || objectIds.contains(objectId),
                    fieldName + " 里的对象[" + objectId + "]不参与" + direction + "，请检查对象id是否正确",
                    HttpStatus.BAD_REQUEST);
        });
    }

    /** 该绑定行取值时是否需要实例数据：入参且来源为对象属性/对象/List（固定值与外部输入不需要） */
    private boolean isInstanceInputRow(ActionParamMappingVO mapping) {
        var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
        return mapping.getCategory() == FunctionParamCategoryEnum.INPUT
                && (sourceType == ActionParamSourceEnum.PROPERTY
                || sourceType == ActionParamSourceEnum.OBJECT
                || sourceType == ActionParamSourceEnum.LIST);
    }

    /** 该出参绑定行是否要把值写回对象属性：只有这种行才有「写到哪个实例、哪一行」可言 */
    private boolean isPropertyWriteOutputRow(ActionParamMappingVO mapping) {
        return mapping.getCategory() == FunctionParamCategoryEnum.OUTPUT
                && mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY;
    }

    /**
     * 某个算子参数在当前绑定里需要数据位置的对象。
     * <p>子字段绑定行也记顶层参数 id，所以一个参数名可能对应多条绑定、跨多个对象；
     * 老数据的绑定行没写对象 id，按行为所属对象算。</p>
     */
    private Set<String> paramDataObjectIds(List<ActionParamMappingVO> mappings, List<FunctionParameterVO> params,
                                            String paramName, Predicate<ActionParamMappingVO> needsTarget,
                                            String legacyObjectId) {
        var paramIds = CollectionUtils.isEmpty(params) ? Set.<Long>of()
                : params.stream().filter(p -> StringUtils.equals(paramName, p.getParamName()))
                .map(FunctionParameterVO::getParamId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> objectIds = new LinkedHashSet<>();
        if (paramIds.isEmpty() || CollectionUtils.isEmpty(mappings)) {
            return objectIds;
        }
        for (var mapping : mappings) {
            if (!needsTarget.test(mapping) || mapping.getFunctionParamId() == null
                    || !paramIds.contains(mapping.getFunctionParamId())) {
                continue;
            }
            var objectId = StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())
                    ? mapping.getOntologyUniqueIdentifier() : legacyObjectId;
            if (StringUtils.isNotEmpty(objectId)) {
                objectIds.add(objectId);
            }
        }
        return objectIds;
    }

    /** 一条绑定行所属的顶层算子参数名；行没记参数 id（不绑算子的行为）时返回 null */
    private String paramNameOf(List<FunctionParameterVO> params, Long functionParamId) {
        if (functionParamId == null || CollectionUtils.isEmpty(params)) {
            return null;
        }
        return params.stream().filter(p -> functionParamId.equals(p.getParamId()))
                .map(FunctionParameterVO::getParamName).findFirst().orElse(null);
    }

    /** 算子某方向的参数定义；行为不绑算子时返回空 */
    private List<FunctionParameterVO> functionParamsOf(ActionContextInfoDTO context, FunctionParamCategoryEnum category) {
        var functionDetailVO = context.getFunctionDetailVO();
        if (functionDetailVO == null || CollectionUtils.isEmpty(functionDetailVO.getParams())) {
            return List.of();
        }
        return functionDetailVO.getParams().stream()
                .filter(v -> category.equals(v.getCategory()))
                .collect(Collectors.toList());
    }

    /**
     * 按算子参数（inputParams / outputParams）指定的数据位置校验。
     * <p>入参按算子入参名定位；出参按「哪条写属性绑定」定位，key 可以是属性唯一标识、绑定行的出参字段名
     * 或顶层算子出参名（脚本算子往往只有一个顶层出参，属性级写回实例只能靠前两种）。
     * key 写错、该 key 没有对应的绑定行、绑定跨多对象却指定实例主键——
     * 这几种都会静默退回共用的实例选择，读写到调用方没打算碰的实例，直接报错。
     * 入参指定的实例只决定这一路读哪份数据，不影响关系边：绑定落在关系端点对象上时按入参给的实例读，
     * 与 from/to 选了哪颗实例无关。对端对象只给分组行、不给实例时仍拒——那组行会被遍历到的实例覆盖。</p>
     */
    private void checkParamTargets(OntologyActionDetailVO detail, FunctionDetailVO functionDetailVO,
                                   EntityActionExecuteParam param) {
        var inputTargets = param == null ? null : param.getInputParams();
        var elementTargets = param == null ? null : param.getInputElementParams();
        var fieldTargets = param == null ? null : param.getInputFieldParams();
        var outputTargets = param == null ? null : param.getOutputParams();
        if (MapUtils.isEmpty(inputTargets) && MapUtils.isEmpty(elementTargets)
                && MapUtils.isEmpty(fieldTargets) && MapUtils.isEmpty(outputTargets)) {
            return;
        }
        PreconditionUtils.checkArgument(functionDetailVO != null,
                "不绑算子的行为不支持用 inputParams、outputParams 按参数指定数据位置：" + detail.getActionApi(),
                HttpStatus.BAD_REQUEST);
        var linkGroup = actionLinkGroup(detail);
        var params = functionDetailVO.getParams();
        checkDirectionTargets(detail, params, inputTargets, this::isInstanceInputRow,
                FunctionParamCategoryEnum.INPUT, "inputParams", "入参",
                counterpartObjectOf(linkGroup, param));
        checkInputElementTargets(detail, params, elementTargets);
        checkInputFieldTargets(detail, params, fieldTargets);
        if (MapUtils.isNotEmpty(inputTargets) && MapUtils.isNotEmpty(elementTargets)) {
            elementTargets.keySet().stream().filter(inputTargets::containsKey).findFirst().ifPresent(key -> {
                throw new BusinessException("入参[" + key + "]同时按参数名和按元素给了数据位置，同一入参只用一种写法",
                        HttpStatus.BAD_REQUEST);
            });
        }
        if (MapUtils.isNotEmpty(fieldTargets)) {
            fieldTargets.keySet().stream().filter(key -> MapUtils.isNotEmpty(inputTargets) && inputTargets.containsKey(key))
                    .findFirst().ifPresent(key -> {
                        throw new BusinessException("入参[" + key + "]同时按参数名和按子级给了数据位置，同一入参只用一种写法",
                                HttpStatus.BAD_REQUEST);
                    });
            fieldTargets.keySet().stream().filter(key -> MapUtils.isNotEmpty(elementTargets) && elementTargets.containsKey(key))
                    .findFirst().ifPresent(key -> {
                        throw new BusinessException("入参[" + key + "]同时按元素和按子级给了数据位置，同一入参只用一种写法",
                                HttpStatus.BAD_REQUEST);
                    });
        }
        checkOutputTargets(detail, params, outputTargets);
    }

    /**
     * LIST 入参按子级给的数据位置：每个子级只给一个位置，位置里至少要有实例主键或分组行主键。
     * <p>同一入参的子级可以来自不同对象、不同实例，所以不要求各子级指到同一个位置；
     * 没给分组行的子级按该子表默认口径读。</p>
     */
    private void checkInputFieldTargets(OntologyActionDetailVO detail, List<FunctionParameterVO> allParams,
                                        Map<String, Map<String, ActionInstanceTargetParam>> fieldTargets) {
        if (MapUtils.isEmpty(fieldTargets)) {
            return;
        }
        var params = allParams.stream().filter(v -> v.getCategory() == FunctionParamCategoryEnum.INPUT)
                .collect(Collectors.toList());
        fieldTargets.forEach((paramName, byProperty) -> {
            var def = params.stream().filter(p -> StringUtils.equals(paramName, p.getParamName()))
                    .findFirst().orElse(null);
            PreconditionUtils.checkNotNull(def,
                    "inputParams 里的入参[" + paramName + "]不是该算子的入参，请检查名称是否正确", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(def.paramTypeEnum() == FunctionParamTypeEnum.LIST,
                    "入参[" + paramName + "]不是列表类型，不能按子级给数据位置", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(MapUtils.isNotEmpty(byProperty),
                    "入参[" + paramName + "]的子级数据位置不能为空", HttpStatus.BAD_REQUEST);
            var mappingIns = CollectionUtils.emptyIfNull(detail.getMappingIns());
            byProperty.forEach((propertyId, position) -> {
                var mapping = mappingIns.stream()
                        .filter(row -> Objects.equals(def.getParamId(), row.getFunctionParamId()))
                        .filter(row -> StringUtils.equals(propertyId, row.getPropertyUniqueIdentifier()))
                        .findFirst().orElse(null);
                var childName = mapping == null ? propertyId
                        : StringUtils.defaultIfBlank(mapping.getParamPath(),
                        StringUtils.defaultIfBlank(mapping.getParameterName(), propertyId));
                var label = "入参[" + paramName + "]的子级[" + childName + "]";
                PreconditionUtils.checkNotNull(position, label + "的数据位置不能为空", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(position.getEntityPrimaryKey() != null
                                || MapUtils.isNotEmpty(position.getDataPrimaryKeys()),
                        label + "没给实例主键，也没给分组行主键", HttpStatus.BAD_REQUEST);
            });
        });
    }

    /**
     * LIST 入参按元素给的数据位置：一个元素就是列表的一项，各自指定对象、实例和分组行。
     * <p>同一份列表的元素可以来自不同对象、不同实例；入参的绑定跨多个对象时每个元素都要点名对象。
     * 同一入参不能既按参数名给一整个位置、又按元素逐个给。</p>
     */
    private void checkInputElementTargets(OntologyActionDetailVO detail, List<FunctionParameterVO> allParams,
                                          Map<String, List<ActionInstanceTargetParam>> elementTargets) {
        if (MapUtils.isEmpty(elementTargets)) {
            return;
        }
        var params = allParams.stream().filter(v -> v.getCategory() == FunctionParamCategoryEnum.INPUT)
                .collect(Collectors.toList());
        var legacyObjectId = legacyActionObject(detail.getOntologyUniqIdentifier());
        elementTargets.forEach((paramName, elements) -> {
            var def = params.stream().filter(p -> StringUtils.equals(paramName, p.getParamName()))
                    .findFirst().orElse(null);
            PreconditionUtils.checkNotNull(def,
                    "inputParams 里的入参[" + paramName + "]不是该算子的入参，请检查名称是否正确", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(def.paramTypeEnum() == FunctionParamTypeEnum.LIST,
                    "入参[" + paramName + "]不是列表类型，不能按元素给数据位置", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(elements),
                    "入参[" + paramName + "]的数据位置数组不能为空", HttpStatus.BAD_REQUEST);
            var objectIds = paramDataObjectIds(detail.getMappingIns(), params, paramName, this::isInstanceInputRow,
                    legacyObjectId);
            PreconditionUtils.checkArgument(!objectIds.isEmpty(),
                    "入参[" + paramName + "]的绑定没有取对象属性的行，按元素指定数据位置无效", HttpStatus.BAD_REQUEST);
            var paramId = def.getParamId();
            var wholePackage = CollectionUtils.isNotEmpty(detail.getMappingIns()) && detail.getMappingIns().stream()
                    .anyMatch(row -> Objects.equals(row.getFunctionParamId(), paramId) && isInstanceInputRow(row)
                            && StringUtils.isEmpty(row.getParamPath()));
            PreconditionUtils.checkArgument(!wholePackage,
                    "入参[" + paramName + "]是整包取值（绑定行没有子字段路径），不能按元素给数据位置",
                    HttpStatus.BAD_REQUEST);
            for (int index = 0; index < elements.size(); index++) {
                var label = "入参[" + paramName + "]第 " + (index + 1) + " 个元素";
                var element = elements.get(index);
                PreconditionUtils.checkNotNull(element, label + "的数据位置不能为空", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(element.getEntityPrimaryKey() != null
                                || MapUtils.isNotEmpty(element.getDataPrimaryKeys()),
                        label + "没给实例主键，也没给分组行主键", HttpStatus.BAD_REQUEST);
                var objectId = StringUtils.isNotEmpty(element.getOntologyIdentifier())
                        ? element.getOntologyIdentifier()
                        : (objectIds.size() == 1 ? objectIds.iterator().next() : null);
                PreconditionUtils.checkArgument(objectId != null,
                        label + "没说读哪个对象：入参[" + paramName + "]的绑定跨多个对象（"
                                + String.join("、", objectIds) + "），每个元素都要传 ontologyIdentifier",
                        HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(objectIds.contains(objectId),
                        label + "指定的对象[" + objectId + "]不参与入参读取，请检查对象id是否正确",
                        HttpStatus.BAD_REQUEST);
            }
        });
    }

    private void checkDirectionTargets(OntologyActionDetailVO detail, List<FunctionParameterVO> allParams,
                                       Map<String, ActionInstanceTargetParam> targets,
                                       Predicate<ActionParamMappingVO> needsTarget,
                                       FunctionParamCategoryEnum category, String fieldName, String cnLabel,
                                       String counterpartObject) {
        if (MapUtils.isEmpty(targets)) {
            return;
        }
        var params = allParams.stream().filter(v -> v.getCategory() == category).collect(Collectors.toList());
        var paramNames = params.stream().map(FunctionParameterVO::getParamName).collect(Collectors.toSet());
        targets.forEach((paramName, target) -> {
            PreconditionUtils.checkArgument(paramNames.contains(paramName),
                    fieldName + " 里的" + cnLabel + "[" + paramName + "]不是该算子的" + cnLabel + "，请检查名称是否正确",
                    HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(target != null
                            && (target.getEntityPrimaryKey() != null || MapUtils.isNotEmpty(target.getDataPrimaryKeys())),
                    fieldName + " 里的" + cnLabel + "[" + paramName + "]没给实例主键，也没给分组行主键", HttpStatus.BAD_REQUEST);
            var objectIds = paramDataObjectIds(detail.getMappingIns(), params, paramName, needsTarget,
                    legacyActionObject(detail.getOntologyUniqIdentifier()));
            PreconditionUtils.checkArgument(!objectIds.isEmpty(),
                    cnLabel + "[" + paramName + "]的绑定没有" + ("入参".equals(cnLabel) ? "取对象属性" : "写入对象属性")
                            + "的行，按" + cnLabel + "指定数据位置无效", HttpStatus.BAD_REQUEST);
            // 对端对象明确、又只按入参给了分组行没给实例时，这组行会被本次遍历到的实例盖掉，直接拒；
            // 给了实例则按该实例读，只影响这一路取值，关系边仍由 from/to 决定
            if (StringUtils.isNotEmpty(counterpartObject) && objectIds.contains(counterpartObject)
                    && target.getEntityPrimaryKey() == null) {
                PreconditionUtils.checkArgument(MapUtils.isEmpty(target.getDataPrimaryKeys()),
                        cnLabel + "[" + paramName + "]的数据落在关系对端对象[" + counterpartObject + "]上，"
                                + "分组行由关系两端决定，不能按" + cnLabel + "指定", HttpStatus.BAD_REQUEST);
            }
            PreconditionUtils.checkArgument(target.getEntityPrimaryKey() == null || objectIds.size() == 1,
                    cnLabel + "[" + paramName + "]的绑定跨多个对象，不能按" + cnLabel + "指定实例主键，请用 "
                            + "entityPrimaryKeys 按对象传",
                    HttpStatus.BAD_REQUEST);
        });
    }

    /**
     * 校验 outputParams 的 key 是否指向该行为里真实存在的写属性出参绑定。
     * <p>出参写回的单位是「一条绑定行 = 一个属性」，不是「一个算子出参」，所以同一份返回体的几个字段
     * 可以各写一个实例。key 的匹配优先级：属性唯一标识 &gt; 绑定行的出参字段名 &gt; 该行所属的顶层算子出参名；
     * 字段名命中多条绑定又分属不同对象时不猜实例，要求改用属性唯一标识。</p>
     * <p>写回目标是关系对端对象时照样按 key 指定的实例落库，不受 from/to 遍历影响：
     * 关系两端选哪颗实例只决定关系边和入参读取，出参写到哪里由这条 key 决定。</p>
     */
    private void checkOutputTargets(OntologyActionDetailVO detail, List<FunctionParameterVO> allParams,
                                    Map<String, ActionInstanceTargetParam> targets) {
        if (MapUtils.isEmpty(targets)) {
            return;
        }
        var outputParams = allParams.stream()
                .filter(v -> v.getCategory() == FunctionParamCategoryEnum.OUTPUT)
                .collect(Collectors.toList());
        var writeRows = CollectionUtils.isEmpty(detail.getMappingIns()) ? List.<ActionParamMappingVO>of()
                : detail.getMappingIns().stream().filter(this::isPropertyWriteOutputRow).collect(Collectors.toList());
        var legacyObjectId = legacyActionObject(detail.getOntologyUniqIdentifier());
        targets.forEach((key, target) -> {
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(key),
                    "outputParams 的 key 不能为空", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(target != null
                            && (target.getEntityPrimaryKey() != null || MapUtils.isNotEmpty(target.getDataPrimaryKeys())),
                    "outputParams 里的出参[" + key + "]没给实例主键，也没给分组行主键", HttpStatus.BAD_REQUEST);
            var rows = writeRows.stream()
                    .filter(row -> key.equals(outputTargetKeyOf(row, outputParams, targets)))
                    .collect(Collectors.toList());
            PreconditionUtils.checkArgument(!rows.isEmpty(),
                    "outputParams 里的[" + key + "]没有命中任何写入对象属性的出参绑定"
                            + "（或命中的绑定已被更精确的 key 点名）。可指定的写属性出参："
                            + describeOutputWriteRows(writeRows, outputParams, legacyObjectId),
                    HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(rows.stream().anyMatch(row -> StringUtils.isNotEmpty(row.getFunctionParamExpression())),
                    "出参[" + key + "]命中的绑定都没配置取值路径，按它指定数据位置不会写回任何属性", HttpStatus.BAD_REQUEST);
            var objectIds = rows.stream()
                    .map(row -> StringUtils.isNotEmpty(row.getOntologyUniqueIdentifier())
                            ? row.getOntologyUniqueIdentifier() : legacyObjectId)
                    .filter(StringUtils::isNotEmpty)
                    .distinct()
                    .collect(Collectors.toList());
            PreconditionUtils.checkArgument(target.getEntityPrimaryKey() == null || objectIds.size() == 1,
                    "出参[" + key + "]命中多条绑定且分属对象[" + String.join("、", objectIds)
                            + "]，不能按它指定实例主键，请改用各行的属性唯一标识作 key",
                    HttpStatus.BAD_REQUEST);
        });
    }

    /** 出参 key 与某条写属性绑定的匹配层级：3 属性唯一标识、2 出参字段名、1 顶层算子出参名、0 不匹配 */
    private int outputTargetTier(ActionParamMappingVO row, List<FunctionParameterVO> outputParams, String key) {
        if (StringUtils.isEmpty(key)) {
            return 0;
        }
        if (key.equals(row.getPropertyUniqueIdentifier())) {
            return 3;
        }
        if (key.equals(row.getParameterName())) {
            return 2;
        }
        return key.equals(paramNameOf(outputParams, row.getFunctionParamId())) ? 1 : 0;
    }

    /**
     * 一条写属性出参绑定归属 outputParams 里的哪个 key：取点名它的 key 中优先级最高的那个，
     * 同优先级按请求里的出现顺序取第一个；没有 key 点名该行返回 null。
     * <p>校验侧和写回侧共用它，报错里「这个 key 命中几条绑定」与实际「这几条按哪个实例写回」才是同一批。</p>
     */
    private String outputTargetKeyOf(ActionParamMappingVO row, List<FunctionParameterVO> outputParams,
                                     Map<String, ActionInstanceTargetParam> targets) {
        if (MapUtils.isEmpty(targets)) {
            return null;
        }
        String bestKey = null;
        var bestTier = 0;
        for (var entry : targets.entrySet()) {
            var tier = outputTargetTier(row, outputParams, entry.getKey());
            if (tier > bestTier) {
                bestTier = tier;
                bestKey = entry.getKey();
            }
        }
        return bestKey;
    }

    /** 报错时列出该行为可被 outputParams 指定的写属性出参，让调用方直接照抄 key */
    private String describeOutputWriteRows(List<ActionParamMappingVO> rows, List<FunctionParameterVO> outputParams,
                                           String legacyObjectId) {
        if (rows.isEmpty()) {
            return "无（该行为没有把出参写入对象属性的绑定）";
        }
        var limit = 10;
        var shown = rows.stream().limit(limit).map(row -> {
            var paramName = StringUtils.defaultIfBlank(row.getParameterName(),
                    paramNameOf(outputParams, row.getFunctionParamId()));
            var objectId = StringUtils.isNotEmpty(row.getOntologyUniqueIdentifier())
                    ? row.getOntologyUniqueIdentifier() : legacyObjectId;
            return StringUtils.defaultIfBlank(row.getPropertyUniqueIdentifier(), "无属性id")
                    + "(" + StringUtils.defaultIfBlank(row.getDisplayName(), "未命名属性")
                    + "，字段名=" + StringUtils.defaultIfBlank(paramName, "无")
                    + "，对象=" + StringUtils.defaultIfBlank(objectId, "无") + ")";
        }).collect(Collectors.joining("、"));
        return rows.size() > limit ? shown + " 等 " + rows.size() + " 条" : shown;
    }

    /** 行为绑定的关系定义；没绑关系或关系不存在返回 null */
    private OntologyLinkGroup actionLinkGroup(OntologyActionDetailVO detail) {
        var link = detail.getLinkMapping();
        if (link == null || StringUtils.isEmpty(link.getOntologyLinkUniqIdentifier())) {
            return null;
        }
        return linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, link.getOntologyLinkUniqIdentifier()));
    }

    /**
     * 关系对端对象：请求点名了就用它；否则按本端对象推（对外执行时本端是调用方传的，可靠）。
     * 都推不出来返回 null，调用处不判——宁可放过也不误拦。
     */
    private String counterpartObjectOf(OntologyLinkGroup linkGroup, EntityActionExecuteParam param) {
        if (param == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(param.getLinkedOntologyUniqueIdentifier())) {
            return param.getLinkedOntologyUniqueIdentifier();
        }
        if (linkGroup == null || StringUtils.isEmpty(param.getOntologyUniqueIdentifier())) {
            return null;
        }
        return counterpartOntology(linkGroup, param.getOntologyUniqueIdentifier());
    }

    /** 从行为映射收集需要实例数据的对象：属性/对象/List 来源的入参，以及写入对象属性的出参 */
    private Set<String> collectMappingObjectIds(OntologyActionDetailVO detail) {
        return collectMappingObjectIds(detail, null);
    }

    /**
     * @param inputOnly true 只收属性入参涉及的对象，false 只收写出参涉及的对象，null 收两者；
     *                  按方向拆实例主键时要区分对象是被读还是被写
     */
    private Set<String> collectMappingObjectIds(OntologyActionDetailVO detail, Boolean inputOnly) {
        Set<String> objectIds = new LinkedHashSet<>();
        if (detail == null || CollectionUtils.isEmpty(detail.getMappingIns())) {
            return objectIds;
        }
        for (var mapping : detail.getMappingIns()) {
            var inputNeedsInstance = isInstanceInputRow(mapping);
            var outputWritesProperty = mapping.getCategory() == FunctionParamCategoryEnum.OUTPUT
                    && mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY;
            if (Boolean.TRUE.equals(inputOnly) && !inputNeedsInstance) {
                continue;
            }
            if (Boolean.FALSE.equals(inputOnly) && !outputWritesProperty) {
                continue;
            }
            if ((inputNeedsInstance || outputWritesProperty) && StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())) {
                objectIds.add(mapping.getOntologyUniqueIdentifier());
            }
        }
        return objectIds;
    }

    /**
     * 取某个对象本次执行选中的实例主键：关系对端优先用本次遍历到的实例，
     * 其余对象取显式传入的 map，最后回退到单对象参数。
     */
    private Object resolveEntityPrimaryKey(ActionContextInfoDTO context, EntityActionExecuteParam param, String objectId) {
        return resolveEntityPrimaryKey(context, param, objectId, null);
    }

    /**
     * targets 是调用方按方向（入参读取 / 出参写回）单独指定的数据位置，命中时优先于共用的 entityPrimaryKeys，
     * 使同一对象可以读一个实例、写另一个实例；关系对端仍由本次遍历到的实例决定，不参与方向拆分。
     */
    private Object resolveEntityPrimaryKey(ActionContextInfoDTO context, EntityActionExecuteParam param, String objectId,
                                          Map<String, ActionInstanceTargetParam> targets) {
        if (StringUtils.equals(objectId, context.getLinkToOntologyUniqueIdentifier())
                && context.getLinkEntityPrimaryKey() != null) {
            return context.getLinkEntityPrimaryKey();
        }
        var target = targets == null ? null : targets.get(objectId);
        if (target != null && target.getEntityPrimaryKey() != null) {
            return target.getEntityPrimaryKey();
        }
        var keys = context.getEntityPrimaryKeys();
        if (keys != null && keys.get(objectId) != null) {
            return keys.get(objectId);
        }
        if (param != null && StringUtils.equals(objectId, param.getOntologyUniqueIdentifier())
                && param.getEntityPrimaryKey() != null) {
            return param.getEntityPrimaryKey();
        }
        return null;
    }

    /**
     * 读取方向兜底的实例：该对象在 inputParams 里被某个入参点名过实例时，集合入参不选也能按这个实例读，
     * 没指定行的集合属性按默认口径取该实例最近的 10 行。写回不参与，落哪个实例必须显式选。
     * <p>同一对象的多个入参给了不同实例时没有唯一答案，返回 null，仍按未选择实例报错。</p>
     */
    private Object paramTargetPrimaryKey(ActionContextInfoDTO context, EntityActionExecuteParam param, String objectId) {
        var targets = param == null ? null : param.getInputParams();
        var elementTargets = param == null ? null : param.getInputElementParams();
        if ((MapUtils.isEmpty(targets) && MapUtils.isEmpty(elementTargets)) || StringUtils.isEmpty(objectId)) {
            return null;
        }
        var legacyObjectId = legacyActionObject(context, param);
        Set<Object> primaryKeys = new LinkedHashSet<>();
        if (MapUtils.isNotEmpty(targets)) {
            targets.forEach((paramName, target) -> {
                var primaryKey = target == null ? null : target.getEntityPrimaryKey();
                if (primaryKey != null && paramDataObjectIds(context.getMappings(), context.getFunctionInputParams(),
                        paramName, this::isInstanceInputRow, legacyObjectId).contains(objectId)) {
                    primaryKeys.add(primaryKey);
                }
            });
        }
        // 按元素给位置的入参：指向该对象的元素必须都点了实例，才算兜底有唯一答案
        if (MapUtils.isNotEmpty(elementTargets)) {
            elementTargets.forEach((paramName, elements) -> {
                var objectIds = paramDataObjectIds(context.getMappings(), context.getFunctionInputParams(),
                        paramName, this::isInstanceInputRow, legacyObjectId);
                if (CollectionUtils.isEmpty(elements)) {
                    return;
                }
                var forObject = elements.stream()
                        .filter(element -> element != null && StringUtils.equals(objectId,
                                StringUtils.isNotEmpty(element.getOntologyIdentifier())
                                        ? element.getOntologyIdentifier()
                                        : (objectIds.size() == 1 ? objectId : null)))
                        .collect(Collectors.toList());
                if (forObject.isEmpty() || forObject.stream().anyMatch(element -> element.getEntityPrimaryKey() == null)) {
                    return;
                }
                forObject.forEach(element -> primaryKeys.add(element.getEntityPrimaryKey()));
            });
        }
        return primaryKeys.size() == 1 ? primaryKeys.iterator().next() : null;
    }

    /** 调用方按入参方向指定的数据位置；没指定返回 null，读取回退到各对象共用的实例选择 */
    private Map<String, ActionInstanceTargetParam> inputTargetsFor(ActionContextInfoDTO context) {
        var param = context.getEntityActionExecuteParam();
        return param == null ? null : param.getInputEntities();
    }

    /** 调用方按出参方向指定的数据位置；没指定返回 null，写回回退到各对象共用的实例选择 */
    private Map<String, ActionInstanceTargetParam> outputTargetsFor(ActionContextInfoDTO context) {
        var param = context.getEntityActionExecuteParam();
        return param == null ? null : param.getOutputEntities();
    }

    /** 调用方显式传的某对象实例主键；没传返回 null，由调用处回退到推导 */
    private Object endpointPrimaryKey(ActionContextInfoDTO context, String objectId) {
        return context.getEntityPrimaryKeys() == null ? null : context.getEntityPrimaryKeys().get(objectId);
    }

    /**
     * 按映射涉及的对象逐个加载实例属性，属性id作为key合并。
     * <p>只有入参取对象属性、出参写属性才需要实例；入参全是固定值的（如基础算子的条件/聚合查询）不碰实例。
     * 老数据的映射行没写对象id，回退到行为所属对象，保持改造前的取值口径。</p>
     */
    private Map<String, List<Object>> loadEntityDetailMap(ActionContextInfoDTO context, EntityActionExecuteParam param,
                                                          String excludeObjectId) {
        var details = Lists.<EntityPropertyDetailVO>newArrayList();
        var objectIds = collectMappingObjectIds(context.getActionDetailVO());
        // 只被出参写入的对象按出参方向选实例，其余按入参方向：同一对象可以读一个实例、写另一个实例
        var readObjectIds = collectMappingObjectIds(context.getActionDetailVO(), true);
        if (param != null && StringUtils.isNotEmpty(param.getOntologyUniqueIdentifier())
                && legacyMappingNeedsInstance(context.getActionDetailVO())) {
            objectIds.add(param.getOntologyUniqueIdentifier());
            readObjectIds.add(param.getOntologyUniqueIdentifier());
        }
        var coveredObjects = paramCoveredObjects(context, param, excludeObjectId);
        for (var objectId : objectIds) {
            // 关系对端由本次遍历到的实例提供，不再按选中的单个实例取
            if (StringUtils.equals(objectId, excludeObjectId)) {
                continue;
            }
            // 该对象需要实例的行（属性入参、写属性出参）已全部按参数各自指定了实例，共用视图不再加载它：
            // 调用方只按参数传实例时不必再在 entityPrimaryKeys 里补一份，否则会被"未选择实例数据"拦住
            if (coveredObjects.contains(objectId)) {
                continue;
            }
            var readsObject = readObjectIds.contains(objectId);
            var primaryKey = resolveEntityPrimaryKey(context, param, objectId,
                    readsObject ? inputTargetsFor(context) : outputTargetsFor(context));
            // 集合入参没选实例时，按同一对象在别的入参上选过的实例兜底读取；写回方向仍需显式选实例
            if (primaryKey == null && readsObject) {
                primaryKey = paramTargetPrimaryKey(context, param, objectId);
            }
            PreconditionUtils.checkNotNull(primaryKey,
                    "对象[" + objectId + "]未选择实例数据，无法执行行为："
                            + (context.getActionDetailVO() == null ? "" : context.getActionDetailVO().getActionApi()),
                    HttpStatus.BAD_REQUEST);
            // 共用视图不带读取规则：规则按入参生效，带规则的入参在下面各自建视图
            details.addAll(getEntityDetail(objectId, primaryKey, inputRowKeysFor(context, objectId), null, null));
        }
        return details.stream().collect(Collectors.toMap(EntityPropertyDetailVO::getPropertyUniqIdentifier,
                EntityPropertyDetailVO::getPropertyValues, (list1, list2) -> list1));
    }

    /**
     * 按算子入参单独加载的实例数据：入参名 -> (属性id -> 值)。
     * <p>同一对象的两个属性入参要读不同实例时靠这份视图路由取值。入参只给了分组行主键时，
     * 实例沿用对象级选择、行按指定的取；关系对端不按方向取实例，跳过它、取值回落共用视图——
     * 但该入参显式点了实例时按点的实例读（含关系端点），只影响这一路取值，不改关系边。</p>
     * <p>入参绑定了 List 行数（rowLimit）时即使没给 inputParams 也要单独建视图：共用视图按默认 10 行取，
     * 拿不到用户要的行数。配了子表读取规则的入参同理，规则只在自己的视图里生效，
     * 两个入参绑同一对象同一分组时可以各按各的规则读。</p>
     */
    private Map<String, Map<String, List<Object>>> loadParamEntityDetailMaps(ActionContextInfoDTO context,
                                                                             EntityActionExecuteParam param,
                                                                             String excludeObjectId) {
        var targets = param == null ? null : param.getInputParams();
        var fieldTargets = param == null ? null : param.getInputFieldParams();
        var rowLimits = listRowLimits(context);
        var readRuleParams = context.getListReadRules() == null
                ? Map.<String, Map<String, Map<String, ActionTestListReadRuleParam>>>of()
                : context.getListReadRules();
        if (MapUtils.isEmpty(targets) && MapUtils.isEmpty(fieldTargets)
                && rowLimits.isEmpty() && readRuleParams.isEmpty()) {
            return null;
        }
        var detail = context.getActionDetailVO();
        var legacyObjectId = legacyActionObject(context, param);
        var actionApi = detail == null ? "" : detail.getActionApi();
        Set<String> paramNames = new LinkedHashSet<>();
        if (MapUtils.isNotEmpty(targets)) {
            targets.forEach((paramName, target) -> {
                if (target != null) {
                    paramNames.add(paramName);
                }
            });
        }
        if (MapUtils.isNotEmpty(fieldTargets)) {
            paramNames.addAll(fieldTargets.keySet());
        }
        paramNames.addAll(rowLimits.keySet());
        paramNames.addAll(readRuleParams.keySet());
        // 按元素给位置的入参逐元素读，不走入参级视图；只留它的 rowLimit 给元素读取用
        var elementParams = param == null || param.getInputElementParams() == null ? Set.<String>of()
                : param.getInputElementParams().keySet();
        Map<String, Map<String, List<Object>>> res = new LinkedHashMap<>();
        for (var paramName : paramNames) {
            if (elementParams.contains(paramName)) {
                continue;
            }
            var target = targets == null ? null : targets.get(paramName);
            var fieldPositions = fieldTargets == null ? null : fieldTargets.get(paramName);
            var limits = rowLimits.get(paramName);
            var explicitPrimaryKey = target == null ? null : target.getEntityPrimaryKey();
            Map<String, List<Object>> values = new LinkedHashMap<>();
            // 子级全部点了实例时不必再按对象读一遍，未点名的绑定行仍按入参/对象级实例读
            if (needsSharedRead(context, paramName, fieldPositions)) {
                var objectIds = paramDataObjectIds(context.getMappings(), context.getFunctionInputParams(),
                        paramName, this::isInstanceInputRow, legacyObjectId);
                for (var objectId : objectIds) {
                    // 关系对端由本次遍历到的实例提供；该入参显式点了实例时以入参为准
                    if (StringUtils.equals(objectId, excludeObjectId) && explicitPrimaryKey == null) {
                        continue;
                    }
                    var primaryKey = explicitPrimaryKey != null ? explicitPrimaryKey
                            : resolveEntityPrimaryKey(context, param, objectId, inputTargetsFor(context));
                    // 该入参没点名实例：沿用同一对象在别的入参上选过的实例，未指定行就按默认取最近 10 行
                    if (primaryKey == null) {
                        primaryKey = paramTargetPrimaryKey(context, param, objectId);
                    }
                    PreconditionUtils.checkNotNull(primaryKey,
                            "入参[" + paramName + "]的对象[" + objectId + "]未选择实例数据，无法执行行为：" + actionApi,
                            HttpStatus.BAD_REQUEST);
                    var rowKeys = target != null
                            ? mergeRowKeys(target.getDataPrimaryKeys(), rowKeysOf(inputTargetsFor(context), objectId))
                            : inputRowKeysFor(context, objectId);
                    var objectDetails = getEntityDetail(objectId, primaryKey, rowKeys, limits,
                            readRulesForParam(context, paramName, objectId));
                    // 点了实例就必须按它取到数：取不到时回落共用视图会静默读成另一个实例
                    if (explicitPrimaryKey != null) {
                        PreconditionUtils.checkArgument(!objectDetails.isEmpty(),
                                "入参[" + paramName + "]指定的实例[" + explicitPrimaryKey + "]在对象[" + objectId
                                        + "]下没有数据，无法按该实例取值：" + actionApi, HttpStatus.BAD_REQUEST);
                    }
                    objectDetails.forEach(d -> values.putIfAbsent(d.getPropertyUniqIdentifier(), d.getPropertyValues()));
                }
            }
            if (MapUtils.isNotEmpty(fieldPositions)) {
                readFieldPositions(context, param, paramName, fieldPositions, limits, values);
            }
            // 子级或规则各读各的子表时行数可能不同：按下标拼元素，按最短的多值列对齐，多出来的行丢掉
            if (MapUtils.isNotEmpty(fieldPositions) || readRuleParams.containsKey(paramName)) {
                truncateToShortestRows(values);
            }
            if (!values.isEmpty()) {
                res.put(paramName, values);
            }
        }
        return res.isEmpty() ? null : res;
    }

    /**
     * 子级是否还要按入参级口径读：入参整包点了位置，或还有绑定行没被任何子级位置点名。
     * <p>全部子级都点了实例时只按子级读，调用方不必再给 entityPrimaryKeys；只要剩一条没点名，
     * 它仍要靠入参/对象级实例回退，按原口径读。</p>
     */
    private boolean needsSharedRead(ActionContextInfoDTO context, String paramName,
                                    Map<String, ActionInstanceTargetParam> fieldPositions) {
        if (MapUtils.isEmpty(fieldPositions)) {
            return true;
        }
        var paramId = paramIdByName(context.getFunctionInputParams(), paramName);
        if (paramId == null) {
            return true;
        }
        return CollectionUtils.emptyIfNull(context.getMappings()).stream()
                .filter(this::isInstanceInputRow)
                .filter(mapping -> Objects.equals(paramId, mapping.getFunctionParamId()))
                .anyMatch(mapping -> StringUtils.isBlank(mapping.getPropertyUniqueIdentifier())
                        || !fieldPositions.containsKey(mapping.getPropertyUniqueIdentifier()));
    }

    /**
     * 按子级读：每个子级用它自己的实例和分组行取数，没给行就按该子表默认口径读。
     * <p>同一入参的不同子级可以落在不同对象、不同实例上，互不影响；取值按属性唯一标识回填，
     * 元素组装时按数组下标把各子级的序列拼成列表元素。</p>
     */
    private void readFieldPositions(ActionContextInfoDTO context, EntityActionExecuteParam param, String paramName,
                                    Map<String, ActionInstanceTargetParam> positions,
                                    Map<String, Integer> limits, Map<String, List<Object>> values) {
        var legacyObjectId = legacyActionObject(context, param);
        var actionApi = context.getActionDetailVO() == null ? "" : context.getActionDetailVO().getActionApi();
        var rows = instanceRowsOfParam(context, paramName);
        positions.forEach((propertyId, position) -> {
            var mapping = rows.stream()
                    .filter(row -> StringUtils.equals(propertyId, row.getPropertyUniqueIdentifier()))
                    .findFirst().orElse(null);
            PreconditionUtils.checkNotNull(mapping,
                    "入参[" + paramName + "]的子级没找到绑定行，无法读取数据位置：" + propertyId, HttpStatus.BAD_REQUEST);
            var fieldLabel = "入参[" + paramName + "]的子级["
                    + StringUtils.defaultIfBlank(mapping.getParamPath(),
                    StringUtils.defaultIfBlank(mapping.getParameterName(), propertyId)) + "]";
            var objectId = StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())
                    ? mapping.getOntologyUniqueIdentifier() : legacyObjectId;
            PreconditionUtils.checkNotNull(objectId,
                    fieldLabel + "没有绑定对象，无法读取数据位置：" + actionApi, HttpStatus.BAD_REQUEST);
            var primaryKey = position.getEntityPrimaryKey();
            if (primaryKey == null) {
                primaryKey = resolveEntityPrimaryKey(context, param, objectId, inputTargetsFor(context));
            }
            if (primaryKey == null) {
                primaryKey = paramTargetPrimaryKey(context, param, objectId);
            }
            PreconditionUtils.checkNotNull(primaryKey,
                    fieldLabel + "没给实例主键，也无法从其它入参推出实例，请在它的数据位置里传 entityPrimaryKey",
                    HttpStatus.BAD_REQUEST);
            var rowKeys = mergeRowKeys(position.getDataPrimaryKeys(), rowKeysOf(inputTargetsFor(context), objectId));
            // 没给行才按子表读取规则取；给了行以行主键为准
            var objectDetails = getEntityDetail(objectId, primaryKey, rowKeys, limits,
                    readRulesForParam(context, paramName, objectId));
            var detail = objectDetails.stream()
                    .filter(d -> StringUtils.equals(propertyId, d.getPropertyUniqIdentifier()))
                    .findFirst().orElse(null);
            PreconditionUtils.checkNotNull(detail,
                    fieldLabel + "指定的实例[" + primaryKey + "]在对象[" + objectId + "]下没有数据，无法按该子级取值："
                            + actionApi, HttpStatus.BAD_REQUEST);
            values.put(propertyId, detail.getPropertyValues());
        });
    }

    /** 子级各读各的子表时行数可能对不上：按最短的列截齐，单值列（实例级取值）照旧广播到每一行 */
    private static void truncateToShortestRows(Map<String, List<Object>> values) {
        int shortest = values.values().stream()
                .filter(value -> value != null && value.size() > 1)
                .mapToInt(List::size)
                .min().orElse(0);
        if (shortest <= 1) {
            return;
        }
        values.replaceAll((property, value) -> value != null && value.size() > shortest
                ? new ArrayList<>(value.subList(0, shortest)) : value);
    }

    /** 入参按名字找算子参数 id；不绑算子或名字对不上时返回 null */
    private static Long paramIdByName(List<FunctionParameterVO> params, String paramName) {
        if (CollectionUtils.isEmpty(params) || StringUtils.isEmpty(paramName)) {
            return null;
        }
        return params.stream().filter(p -> StringUtils.equals(paramName, p.getParamName()))
                .map(FunctionParameterVO::getParamId).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** 该入参需要读实例的绑定行 */
    private List<ActionParamMappingVO> instanceRowsOfParam(ActionContextInfoDTO context, String paramName) {
        var paramId = paramIdByName(context.getFunctionInputParams(), paramName);
        if (paramId == null) {
            return List.of();
        }
        return CollectionUtils.emptyIfNull(context.getMappings()).stream()
                .filter(this::isInstanceInputRow)
                .filter(mapping -> Objects.equals(paramId, mapping.getFunctionParamId()))
                .collect(Collectors.toList());
    }

    /**
     * LIST 入参按元素给位置时逐元素加载属性：入参名 -> 按元素顺序排列的属性取值。
     * <p>每个元素按自己的对象、实例和分组行读一份数据，元素之间互不影响，同一份列表可以混用对象和实例。
     * 一个元素就是列表的一项，所以每个绑定的属性都必须只读到一行：默认读到的多行说明这个元素该指定分组行。
     * 元素所属对象没有绑定的属性不会出现在该元素里，按元素字段组装时自然跳过。</p>
     */
    private Map<String, List<Map<String, Object>>> loadParamElementDetailMaps(ActionContextInfoDTO context,
                                                                              EntityActionExecuteParam param) {
        var elementTargets = param == null ? null : param.getInputElementParams();
        if (MapUtils.isEmpty(elementTargets)) {
            return null;
        }
        var rowLimits = listRowLimits(context);
        var legacyObjectId = legacyActionObject(context, param);
        var actionApi = context.getActionDetailVO() == null ? "" : context.getActionDetailVO().getActionApi();
        Map<String, List<Map<String, Object>>> res = new LinkedHashMap<>();
        for (var entry : elementTargets.entrySet()) {
            var paramName = entry.getKey();
            var elements = entry.getValue();
            if (CollectionUtils.isEmpty(elements)) {
                continue;
            }
            var objectIds = paramDataObjectIds(context.getMappings(), context.getFunctionInputParams(),
                    paramName, this::isInstanceInputRow, legacyObjectId);
            var limits = rowLimits.get(paramName);
            var elementValues = new ArrayList<Map<String, Object>>(elements.size());
            for (int index = 0; index < elements.size(); index++) {
                var element = elements.get(index);
                var label = "入参[" + paramName + "]第 " + (index + 1) + " 个元素";
                var objectId = StringUtils.isNotEmpty(element.getOntologyIdentifier())
                        ? element.getOntologyIdentifier()
                        : (objectIds.size() == 1 ? objectIds.iterator().next() : null);
                var primaryKey = element.getEntityPrimaryKey();
                if (primaryKey == null) {
                    primaryKey = resolveEntityPrimaryKey(context, param, objectId, inputTargetsFor(context));
                }
                if (primaryKey == null) {
                    primaryKey = paramTargetPrimaryKey(context, param, objectId);
                }
                PreconditionUtils.checkNotNull(primaryKey,
                        label + "的对象[" + objectId + "]未选择实例数据，无法执行行为：" + actionApi,
                        HttpStatus.BAD_REQUEST);
                var rowKeys = mergeRowKeys(element.getDataPrimaryKeys(), rowKeysOf(inputTargetsFor(context), objectId));
                // 元素各自点名了分组行，读取规则不参与（规则只在没逐行指定时生效，且与按元素给位置互斥）
                var details = getEntityDetail(objectId, primaryKey, rowKeys, limits, null);
                PreconditionUtils.checkArgument(!details.isEmpty(),
                        label + "指定的实例[" + primaryKey + "]在对象[" + objectId + "]下没有数据，无法按该元素取值：" + actionApi,
                        HttpStatus.BAD_REQUEST);
                Map<String, Object> values = new LinkedHashMap<>();
                for (var detail : details) {
                    var propertyValues = detail.getPropertyValues();
                    PreconditionUtils.checkArgument(CollectionUtils.size(propertyValues) <= 1,
                            label + "的属性[" + detail.getPropertyUniqIdentifier() + "]读到了 "
                                    + CollectionUtils.size(propertyValues) + " 行：一个元素就是列表的一项，"
                                    + "请在该元素里指定分组行主键", HttpStatus.BAD_REQUEST);
                    values.put(detail.getPropertyUniqIdentifier(),
                            CollectionUtils.isEmpty(propertyValues) ? null : propertyValues.get(0));
                }
                elementValues.add(values);
            }
            res.put(paramName, elementValues);
        }
        return res.isEmpty() ? null : res;
    }

    /**
     * 各入参绑定的读取行数：入参名 -> (属性唯一标识 -> 行数)。
     * <p>同一个分组共用一次多行查询，行数在取数时按分组取最大；这里按属性记，才能只放大该入参绑定属性所在的分组。</p>
     */
    private Map<String, Map<String, Integer>> listRowLimits(ActionContextInfoDTO context) {
        var mappings = context.getMappings();
        if (CollectionUtils.isEmpty(mappings)) {
            return Map.of();
        }
        var inputParams = context.getFunctionInputParams();
        Map<String, Map<String, Integer>> res = new LinkedHashMap<>();
        for (var mapping : mappings) {
            if (mapping.getRowLimit() == null || StringUtils.isEmpty(mapping.getPropertyUniqueIdentifier())) {
                continue;
            }
            var paramName = paramNameOf(inputParams, mapping.getFunctionParamId());
            if (StringUtils.isEmpty(paramName)) {
                continue;
            }
            res.computeIfAbsent(paramName, k -> new LinkedHashMap<>())
                    .merge(mapping.getPropertyUniqueIdentifier(), mapping.getRowLimit(), Math::max);
        }
        return res;
    }

    /**
     * 需要数据位置的入参与出参已全部按参数各自指定了实例主键的对象。
     * <p>对象级实例选择对这些对象没有意义，共用视图再按它取值只会拦住「只按参数传实例」的请求；
     * 只要还有一条入参绑定或写属性出参没被点名，该对象就仍需共用视图。</p>
     */
    private Set<String> paramCoveredObjects(ActionContextInfoDTO context, EntityActionExecuteParam param,
                                            String excludeObjectId) {
        var inputTargets = param == null ? null : param.getInputParams();
        var elementTargets = param == null ? null : param.getInputElementParams();
        var fieldTargets = param == null ? null : param.getInputFieldParams();
        var outputTargets = param == null ? null : param.getOutputParams();
        if (MapUtils.isEmpty(inputTargets) && MapUtils.isEmpty(elementTargets)
                && MapUtils.isEmpty(fieldTargets) && MapUtils.isEmpty(outputTargets)) {
            return Set.of();
        }
        if (CollectionUtils.isEmpty(context.getMappings())) {
            return Set.of();
        }
        var legacyObjectId = legacyActionObject(context, param);
        var elementCoveredObjects = elementCoveredObjects(context, elementTargets, legacyObjectId);
        var fieldCoveredObjects = fieldCoveredObjects(context, fieldTargets, legacyObjectId);
        Set<String> needTargetObjects = new LinkedHashSet<>();
        Set<String> uncoveredObjects = new HashSet<>();
        for (var mapping : context.getMappings()) {
            var isInputRow = isInstanceInputRow(mapping);
            var isOutputRow = isPropertyWriteOutputRow(mapping);
            if (!isInputRow && !isOutputRow) {
                continue;
            }
            var objectId = StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())
                    ? mapping.getOntologyUniqueIdentifier() : legacyObjectId;
            if (StringUtils.isEmpty(objectId) || StringUtils.equals(objectId, excludeObjectId)) {
                continue;
            }
            needTargetObjects.add(objectId);
            var targets = isInputRow ? inputTargets : outputTargets;
            var target = isInputRow
                    ? paramTargetOf(targets, paramNameOf(context.getFunctionInputParams(), mapping.getFunctionParamId()))
                    : outputTargetFor(context, targets, mapping);
            if ((target == null || target.getEntityPrimaryKey() == null)
                    && !(isInputRow && elementCoveredObjects.contains(objectId))
                    && !(isInputRow && fieldCoveredObjects.contains(objectId))) {
                uncoveredObjects.add(objectId);
            }
        }
        needTargetObjects.removeAll(uncoveredObjects);
        return needTargetObjects;
    }

    /**
     * 按子级给位置的入参覆盖到的对象：该对象不是关系对端，且指向它的每条读实例绑定行都点了实例主键。
     * <p>覆盖后这个对象不必再在 entityPrimaryKeys 里选实例；只要有一条绑定行没点名或没给实例，就要回退对象级选择。</p>
     */
    private Set<String> fieldCoveredObjects(ActionContextInfoDTO context,
                                            Map<String, Map<String, ActionInstanceTargetParam>> fieldTargets,
                                            String legacyObjectId) {
        if (MapUtils.isEmpty(fieldTargets)) {
            return Set.of();
        }
        Set<String> covered = new LinkedHashSet<>();
        Set<String> uncovered = new HashSet<>();
        fieldTargets.forEach((paramName, byProperty) -> {
            var objectIds = paramDataObjectIds(context.getMappings(), context.getFunctionInputParams(),
                    paramName, this::isInstanceInputRow, legacyObjectId);
            if (MapUtils.isEmpty(byProperty)) {
                uncovered.addAll(objectIds);
                return;
            }
            for (var objectId : objectIds) {
                var objectRows = instanceRowsOfParam(context, paramName).stream()
                        .filter(mapping -> StringUtils.equals(objectId,
                                StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())
                                        ? mapping.getOntologyUniqueIdentifier() : legacyObjectId))
                        .collect(Collectors.toList());
                if (!objectRows.isEmpty() && objectRows.stream().allMatch(mapping -> {
                    var position = byProperty.get(mapping.getPropertyUniqueIdentifier());
                    return position != null && position.getEntityPrimaryKey() != null;
                })) {
                    covered.add(objectId);
                } else {
                    uncovered.add(objectId);
                }
            }
        });
        covered.removeAll(uncovered);
        return covered;
    }

    /**
     * 按元素给位置的入参覆盖到的对象：该对象不是关系对端，且指向它的每个元素都点了实例主键。
     * <p>覆盖后这个对象不必再在 entityPrimaryKeys 里选实例，元素自己的实例就够读；
     * 只要有一个元素没点实例、要回退对象级选择，就不能算覆盖。</p>
     */
    private Set<String> elementCoveredObjects(ActionContextInfoDTO context,
                                              Map<String, List<ActionInstanceTargetParam>> elementTargets,
                                              String legacyObjectId) {
        if (MapUtils.isEmpty(elementTargets)) {
            return Set.of();
        }
        Set<String> covered = new LinkedHashSet<>();
        Set<String> uncovered = new HashSet<>();
        elementTargets.forEach((paramName, elements) -> {
            var objectIds = paramDataObjectIds(context.getMappings(), context.getFunctionInputParams(),
                    paramName, this::isInstanceInputRow, legacyObjectId);
            if (CollectionUtils.isEmpty(elements)) {
                uncovered.addAll(objectIds);
                return;
            }
            for (var objectId : objectIds) {
                var forObject = elements.stream()
                        .filter(element -> element != null && StringUtils.equals(objectId,
                                StringUtils.isNotEmpty(element.getOntologyIdentifier())
                                        ? element.getOntologyIdentifier()
                                        : (objectIds.size() == 1 ? objectId : null)))
                        .collect(Collectors.toList());
                if (forObject.isEmpty()) {
                    // 这个对象一条元素都没指向：它的绑定行没被按元素点名
                    uncovered.add(objectId);
                } else if (forObject.stream().allMatch(element -> element.getEntityPrimaryKey() != null)) {
                    covered.add(objectId);
                } else {
                    uncovered.add(objectId);
                }
            }
        });
        covered.removeAll(uncovered);
        return covered;
    }

    /**
     * 早期绑定行没记对象 id 时的兜底对象：挂载对象列是逗号分隔的对象id，只有一个时才有唯一答案。
     * <p>挂了多个对象说明每条绑定各行其是，老行缺对象 id 就无从判断，只能按没写对象 id 处理。</p>
     */
    private String legacyActionObject(String objectIdentifiers) {
        if (StringUtils.isBlank(objectIdentifiers)) {
            return null;
        }
        var identifiers = Arrays.stream(objectIdentifiers.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .collect(Collectors.toList());
        return identifiers.size() == 1 ? identifiers.get(0) : null;
    }

    /**
     * 执行期的兜底对象：请求指定的对象最准（旧请求传的就是行为挂载的对象），
     * 请求没指定时才退回行为挂载对象里唯一的那一个。
     */
    private String legacyActionObject(ActionContextInfoDTO context, EntityActionExecuteParam param) {
        if (param != null && StringUtils.isNotEmpty(param.getOntologyUniqueIdentifier())) {
            return param.getOntologyUniqueIdentifier();
        }
        var detail = context == null ? null : context.getActionDetailVO();
        return detail == null ? null : legacyActionObject(detail.getOntologyUniqIdentifier());
    }

    /** 参数级分组行主键优先，某个分组没给时沿用对象级（inputEntities/outputEntities）里该分组的行主键 */
    private Map<String, Object> mergeRowKeys(Map<String, Object> paramLevel, Map<String, Object> objectLevel) {
        if (MapUtils.isEmpty(paramLevel)) {
            return objectLevel;
        }
        if (MapUtils.isEmpty(objectLevel)) {
            return paramLevel;
        }
        var merged = new LinkedHashMap<String, Object>(objectLevel);
        merged.putAll(paramLevel);
        return merged;
    }

    /**
     * 某个对象指定的入参分组行主键（分组名 -> 行主键）。没指定返回空，读取回退到原口径：取最新一行。
     */
    private Map<String, Object> inputRowKeysFor(ActionContextInfoDTO context, String objectId) {
        return rowKeysOf(inputTargetsFor(context), objectId);
    }

    private Map<String, Object> rowKeysOf(Map<String, ActionInstanceTargetParam> targets, String objectId) {
        if (targets == null || StringUtils.isBlank(objectId)) {
            return null;
        }
        var target = targets.get(objectId);
        var byGroup = target == null ? null : target.getDataPrimaryKeys();
        return MapUtils.isEmpty(byGroup) ? null : byGroup;
    }

    private Object rowKeyFor(Map<String, Object> rowKeysByGroup, String storageGroup) {
        return rowKeysByGroup == null || StringUtils.isBlank(storageGroup)
                ? null : rowKeysByGroup.get(storageGroup);
    }

    /** 分组行主键可能是 LIST 入参按元素给的一组行：数组按原顺序逐行读取，单个值仍是一行 */
    private static List<Object> rowKeyList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : new ArrayList<>(list);
        }
        return Lists.newArrayList(value);
    }

    /**
     * 某个出参绑定本次写回落到哪一行：按该条绑定指定的分组行优先，没指定的分组沿用出参方向（outputEntities）里该对象的行。
     * 都没指定返回空，写回回退到原口径：扩展表新增一行。
     */
    private Object outputRowKeyFor(ActionContextInfoDTO context, ActionParamMappingVO row, String objectId,
                                   String storageGroup) {
        var objectLevel = rowKeysOf(outputTargetsFor(context), objectId);
        var paramTarget = outputTargetFor(context, row);
        return rowKeyFor(paramTarget == null ? objectLevel
                : mergeRowKeys(paramTarget.getDataPrimaryKeys(), objectLevel), storageGroup);
    }

    /** 调用方按算子出参指定的数据位置；没指定返回 null，写回回退到按对象的方向选择 */
    private Map<String, ActionInstanceTargetParam> outputParamTargetsFor(ActionContextInfoDTO context) {
        var param = context.getEntityActionExecuteParam();
        return param == null ? null : param.getOutputParams();
    }

    /** 按算子参数指定的数据位置里该参数的条目；参数名为空或没指定该参数返回 null */
    private ActionInstanceTargetParam paramTargetOf(Map<String, ActionInstanceTargetParam> targets, String paramName) {
        return paramName == null || MapUtils.isEmpty(targets) ? null : targets.get(paramName);
    }

    /** 写回时按执行请求里的 outputParams 定位该条绑定，见 {@link #outputTargetFor(ActionContextInfoDTO, Map, ActionParamMappingVO)} */
    private ActionInstanceTargetParam outputTargetFor(ActionContextInfoDTO context, ActionParamMappingVO row) {
        return outputTargetFor(context, outputParamTargetsFor(context), row);
    }

    /**
     * 某条写属性出参绑定在 outputParams 里对应的数据位置；没有 key 点名该条绑定返回 null，写回退回按对象选实例。
     */
    private ActionInstanceTargetParam outputTargetFor(ActionContextInfoDTO context,
                                                      Map<String, ActionInstanceTargetParam> targets,
                                                      ActionParamMappingVO row) {
        var key = outputTargetKeyOf(row, functionParamsOf(context, FunctionParamCategoryEnum.OUTPUT), targets);
        return key == null ? null : targets.get(key);
    }

    /**
     * 出参写回用的实例主键：按该条出参绑定指定的实例优先（同一对象的几个属性各写一个实例靠它区分），
     * 其次按出参方向指定的对象实例，最后回退到各对象共用的实例选择。
     */
    private Object resolveOutputPrimaryKey(ActionContextInfoDTO context, EntityActionExecuteParam param,
                                           ActionParamMappingVO row, String objectId) {
        var target = outputTargetFor(context, row);
        if (target != null && target.getEntityPrimaryKey() != null) {
            return target.getEntityPrimaryKey();
        }
        return resolveEntityPrimaryKey(context, param, objectId, outputTargetsFor(context));
    }

    /**
     * 指定的分组名必须是对象真实存在的扩展分组：分组名写错时按行取值和按行写回都会静默退回原口径，
     * 与其读错行不如直接报错。main 分组的行就是实例本身，传了也不参与按行处理。
     */
    private void checkGroupRowKeys(String ontologyUniqueIdentifier, List<OntologyProperty> props,
                                   Map<String, Object> rowKeysByGroup) {
        if (MapUtils.isEmpty(rowKeysByGroup)) {
            return;
        }
        var groups = props.stream()
                .map(OntologyProperty::getStorageGroup)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        rowKeysByGroup.forEach((storageGroup, rowKey) -> {
            if (StringUtils.isBlank(storageGroup) || "main".equals(storageGroup)) {
                return;
            }
            PreconditionUtils.checkArgument(groups.contains(storageGroup),
                    "对象[" + ontologyUniqueIdentifier + "]没有存储分组[" + storageGroup
                            + "]，无法按指定行读取或写回属性", HttpStatus.BAD_REQUEST);
        });
    }

    /** 映射行没写对象id、但取值或回写需要实例时，只能按行为所属对象补 */
    private boolean legacyMappingNeedsInstance(OntologyActionDetailVO detail) {
        if (detail == null || CollectionUtils.isEmpty(detail.getMappingIns())) {
            return false;
        }
        for (var mapping : detail.getMappingIns()) {
            if (StringUtils.isNotEmpty(mapping.getOntologyUniqueIdentifier())) {
                continue;
            }
            var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
            var inputNeedsInstance = mapping.getCategory() == FunctionParamCategoryEnum.INPUT
                    && (sourceType == ActionParamSourceEnum.PROPERTY
                    || sourceType == ActionParamSourceEnum.OBJECT
                    || sourceType == ActionParamSourceEnum.LIST);
            var outputWritesProperty = mapping.getCategory() == FunctionParamCategoryEnum.OUTPUT
                    && mapping.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY;
            if (inputNeedsInstance || outputWritesProperty) {
                return true;
            }
        }
        return false;
    }


    public String executeEntityAction(EntityActionExecuteParam param, ActionContextInfoDTO infoDTO, List<List<EntityPropertyDetailVO>> linkedEntities) throws Exception {

        var link = infoDTO.getLink();
        var linkedOntology = infoDTO.getLinkToOntologyUniqueIdentifier();
        var functionInputParams = infoDTO.getFunctionInputParams();
        var mappings = infoDTO.getMappings();
        var actionDetailVO = infoDTO.getActionDetailVO();
        var functionDetailVO = infoDTO.getFunctionDetailVO();
        var ontologyProperties = infoDTO.getOntologyProperties();

        // 不绑算子的行为按定义对单个实体落库，不进算子调用与结果回写链路
        if (functionDetailVO == null) {
            return actionService.executeBaseAction(param, infoDTO);
        }

        List<String> executeResult = Lists.newArrayList();

        //存在关联关系,实体数据必须存在
        if (CollectionUtils.isNotEmpty(linkedEntities)) {
            //获取映射涉及对象的实体属性详情；对端实例由下面的遍历逐个提供
            var srcEntityDetailMap = loadEntityDetailMap(infoDTO, param, linkedOntology);
            //按入参指定的实例数据：显式点了实例的按点的取（含关系端点），其余用本次遍历到的实例
            var paramEntityDetailMaps = loadParamEntityDetailMaps(infoDTO, param, linkedOntology);
            // LIST 入参按元素给位置时逐元素读，元素各自的对象/实例与遍历无关，一次加载全用
            var paramElementDetailMaps = loadParamElementDetailMaps(infoDTO, param);

            //和关联对象下的所有实体计算关系
            var taskResults = taskProcessor.processTask(linkedEntities,
                    partitionRecords -> {
                        return partitionRecords.stream().map(linkedEntityDetail -> {
                            try {
                                Map<String, List<Object>> linkedEntityDetailMap = linkedEntityDetail.stream()
                                        .collect(Collectors.toMap(v -> v.getPropertyUniqIdentifier(), v -> v.getPropertyValues()));
                                // 合并两个实体，propertyId作为key
                                Map<String, List<Object>> mergedEntityDetailMap = Stream.of(srcEntityDetailMap, linkedEntityDetailMap)
                                        .flatMap(map -> map.entrySet().stream())
                                        .collect(Collectors.toMap(v -> v.getKey(), v -> v.getValue(),
                                                (list1, list2) -> {
                                                    list1.addAll(list2);
                                                    return list1;
                                                }));
                                ActionContextInfoDTO contextInfoDTO = ActionContextInfoDTO.builder()
                                        .functionInputParams(functionInputParams)
                                        .mappings(mappings)
                                        .entityDetailMap(mergedEntityDetailMap)
                                        .paramEntityDetailMaps(paramEntityDetailMaps)
                                        .paramElementDetailMaps(paramElementDetailMaps)
                                        .externalParams(infoDTO.getExternalParams())
                                        .actionDetailVO(actionDetailVO)
                                        .functionDetailVO(functionDetailVO)
                                        .ontologyProperties(ontologyProperties)
                                        .entityPrimaryKeys(infoDTO.getEntityPrimaryKeys())
                                        .objectProperties(infoDTO.getObjectProperties())
                                        .entityActionExecuteParam(param)
                                        .listReadRules(infoDTO.getListReadRules())
                                        .link(link)
                                        .linkEntityPrimaryKey(linkedEntityDetail.get(0).getEntityPrimaryKey())
                                        .linkToOntologyUniqueIdentifier(linkedOntology)
                                        // 行为节点的取值覆盖要带到算子调用侧，丢了节点上配的黑板/上游出参来源就退化成绑定行自己的来源
                                        .paramValueOverrides(infoDTO.getParamValueOverrides())
                                        // 入参回填容器同理：成功条件表达式要靠它拿本次真实入参
                                        .resolvedInputs(infoDTO.getResolvedInputs())
                                        .build();
                                //执行函数
                                String functionResultJson = callFunction(contextInfoDTO);
                                //如果函数是同步执行，更新实体属性和关系
                                if (isSyncFunctionResult(functionResultJson)) {
                                    updateEntityPropertyAndRelation(functionResultJson, contextInfoDTO);
                                }
                                return functionResultJson;
                            } catch (Exception e) {
                                log.error("函数执行异常：" + linkedEntityDetail.toString(), e);
                                return "";
                            }
                        }).collect(Collectors.toList());
                    },
                    20);
            executeResult.addAll(taskResults.stream().flatMap(v -> v.stream()).collect(Collectors.toList()));
        }

        //无关联关系
        else {
            //获取映射涉及对象的实体属性详情
            var srcEntityDetailMap = loadEntityDetailMap(infoDTO, param, null);
            //函数调用
            var contextInfoDTO = ActionContextInfoDTO.builder()
                    .functionInputParams(functionInputParams)
                    .mappings(mappings)
                    .entityDetailMap(srcEntityDetailMap)
                    .paramEntityDetailMaps(loadParamEntityDetailMaps(infoDTO, param, null))
                    .paramElementDetailMaps(loadParamElementDetailMaps(infoDTO, param))
                    .externalParams(infoDTO.getExternalParams())
                    .actionDetailVO(actionDetailVO)
                    .functionDetailVO(functionDetailVO)
                    .ontologyProperties(ontologyProperties)
                    .entityPrimaryKeys(infoDTO.getEntityPrimaryKeys())
                    .objectProperties(infoDTO.getObjectProperties())
                    .entityActionExecuteParam(param)
                    // 对象模式的子表读取规则（行数/排序）也要带到取值侧
                    .listReadRules(infoDTO.getListReadRules())
                    // 同上：无关系分支也要把节点级取值覆盖带下去
                    .paramValueOverrides(infoDTO.getParamValueOverrides())
                    // 入参回填容器同理：成功条件表达式要靠它拿本次真实入参
                    .resolvedInputs(infoDTO.getResolvedInputs())
                    .build();
            var functionResultJson = callFunction(contextInfoDTO);
            //函数同步执行，更新结果
            if (isSyncFunctionResult(functionResultJson)) {
                updateEntityPropertyAndRelation(functionResultJson, contextInfoDTO);
            }
            executeResult.add(functionResultJson);
        }
        return jsonMapper.writeValueAsString(executeResult);
    }


    @Override
    @Transactional(transactionManager = "datalakeTransactionManager")
    public void updateProperty(EntityUpdateParam param) {
        var primaryKeyColumnName = tableMetadataMapper.queryPrimaryKeyColumnName(param.getSchemaName(), param.getDatasourceId());
        var columnMap = param.getColumnUpdates().stream().collect(Collectors.toMap(v -> v.getDatasourceColumnName(), v -> v.getColumnValue()));
        var primaryKeyValue = datalakeKey(param.getSchemaName(), param.getDatasourceId(), primaryKeyColumnName, param.getPrimaryKeyValue());
        objectMapper.updateObject(param.getSchemaName(), param.getDatasourceId(), columnMap, primaryKeyColumnName, primaryKeyValue);
    }

    @Override
    public void createEntityNodes(String ontologyIdentifier) {
        var primaryProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));

        var titleProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier)
                .eq(OntologyProperty::getIsTitleKey, 1));

        var titleColumn = "";
        if (titleProperty != null
                && primaryProperty != null
                && StringUtils.equals(titleProperty.getDatasourceId(), primaryProperty.getDatasourceId())) {
            titleColumn = titleProperty.getDatasourceColumnName();
        }

        if (primaryProperty != null && StringUtils.isNotEmpty(primaryProperty.getDatasourceId())) {
            syncNodes(ontologyIdentifier, primaryProperty.getDatasourceSchema(), primaryProperty.getDatasourceId(), primaryProperty.getDatasourceColumnName(), titleColumn);
        }
    }

    @Override
    public List<EntityIdsQueryVO> getByEntityIds(List<EntityIdsQueryParam> params) {
        if (CollectionUtils.isEmpty(params)) {
            return Lists.newArrayList();
        }
        return params.stream().map(p -> {
            var entities = getEntities(p.getOntologyUniqueIdentifier(), null, null, 1, Integer.MAX_VALUE, false);
            var entityPrimaryKeys = p.getEntityPrimaryKeys();
            List<EntityInfoVO> entityList = Lists.newArrayList();
            if (CollectionUtils.isEmpty(entityPrimaryKeys)) {
                entityList = entities.getRecords();
            } else {
                var entityMap = entities.getRecords().stream().collect(Collectors.toMap(v -> v.getPrimaryKey(), v -> v));
                entityList = entityPrimaryKeys.stream().map(k -> entityMap.get(k)).filter(v -> v != null).collect(Collectors.toList());
            }
            return EntityIdsQueryVO.builder()
                    .entityList(entityList)
                    .ontologyUniqueIdentifier(p.getOntologyUniqueIdentifier())
                    .build();
        }).collect(Collectors.toList());
    }


    /**
     * 行为树节点给的整包取值按入参声明类型收口：
     * <ul>
     *   <li>LIST 入参收到单值时当作只有一个元素的集合，上游给一个值也能喂集合；</li>
     *   <li>单值入参收到通配出参行展开的整批列时，按原口径取批里第一条非空。</li>
     * </ul>
     */
    private Object coerceOverrideValue(FunctionParameterVO param, Object value) {
        if (param.paramTypeEnum() == FunctionParamTypeEnum.LIST) {
            return value instanceof List ? value : Lists.newArrayList(value);
        }
        if (value instanceof List<?> values) {
            return values.stream().filter(Objects::nonNull).findFirst()
                    .orElseThrow(() -> new BusinessException("行为入参[" + param.getParamName() + "]整批取不到值"));
        }
        return value;
    }

    public String callFunction(ActionContextInfoDTO actionContext) throws Exception {

        //构造函数参数，list类型返回所有值，其他类型取第一个值
        List<FunctionParameter> parameters = actionContext.getFunctionInputParams().stream().map(p -> {
            var paramMappings = actionContext.getMappings().stream()
                    .filter(m -> p.getParamId().equals(m.getFunctionParamId()))
                    .collect(Collectors.toList());
            if (paramMappings.isEmpty()) {
                return new FunctionParameter().setParamName(p.getParamName());
            }
            // 行为节点给这个入参配了取值来源：整包按节点给的值用，不再逐字段拼——子字段行会被同一个值各自盖一遍
            var nodeValue = paramValueOverride(actionContext, p.getParamName());
            if (nodeValue != null) {
                return FunctionParameter.builder()
                        .paramName(p.getParamName())
                        .paramValue(coerceOverrideValue(p, nodeValue))
                        .build();
            }
            // 没有子字段路径即整包给这个参数一个值，与逐字段绑定改造前的行为一致
            boolean boundWhole = paramMappings.stream().noneMatch(m -> StringUtils.isNotEmpty(m.getParamPath()));
            if (boundWhole) {
                return FunctionParameter.builder()
                        .paramName(p.getParamName())
                        .paramValue(resolveInputValue(paramMappings.get(0), p.paramTypeEnum(), actionContext, p.getParamName()))
                        .build();
            }
            return assembleObjectParam(p, paramMappings, actionContext);
        }).collect(Collectors.toList());
        // 基础查询算子的对象标识是机制入参，不在 function_param 中声明，由行为绑定的对象单独补上；
        // 其它函数类型不传，assembleParams/buildInputMap 也会过滤未声明入参
        if (actionContext.getFunctionDetailVO().getType() == FunctionTypeEnum.BASIC_QUERY) {
            parameters.add(FunctionParameter.builder()
                    .paramName(BasicQueryOperator.PARAM_ONTOLOGY_IDENTIFIER)
                    .paramValue(actionContext.getEntityActionExecuteParam().getOntologyUniqueIdentifier())
                    .build());
            // 实例主键同为机制入参：基础算子行为按选中的实例查，查询会拼上「主键 = 该实例」；没给实例时维持原来的全量口径
            var entityPrimaryKey = actionContext.getEntityActionExecuteParam().getEntityPrimaryKey();
            if (entityPrimaryKey != null) {
                parameters.add(FunctionParameter.builder()
                        .paramName(BasicQueryOperator.PARAM_ENTITY_PRIMARY_KEY)
                        .paramValue(entityPrimaryKey)
                        .build());
            }
        }
        // 行为树的成功条件表达式要读本次真实入参：调用方给了回填容器就写进去；
        // 关系行为按对端逐对执行，多个线程共用一个容器，加锁后最后完成的一条覆盖
        var resolvedInputs = actionContext.getResolvedInputs();
        if (resolvedInputs != null) {
            synchronized (resolvedInputs) {
                resolvedInputs.clear();
                for (var parameter : parameters) {
                    if (StringUtils.isNotEmpty(parameter.getParamName())) {
                        resolvedInputs.put(parameter.getParamName(), parameter.getParamValue());
                    }
                }
            }
        }
        //执行函数
        // 取完整返回体（不剥 FunctionResultVO 外壳）：关系落库的判据 startTime/endTime/timeWindows 只在外壳上，
        // 剥壳后在到达行为前就丢了；出参取值路径同样按这层完整返回体书写，与测试回显一致
        var functionResultJson = functionService.executeFunction(FunctionExecuteParam.builder()
                .functionApi(actionContext.getActionDetailVO().getFunctionApi())
                .ontologySpaceId(actionContext.getActionDetailVO().getOntologySpaceId())
                .parameters(parameters)
                .build(), true);

        var functionResult = readFunctionResult(functionResultJson);
        //异步接口，需要保存调用时的上下文信息
        if (functionResult != null && StringUtils.isNotEmpty(functionResult.getTaskId())) {
            var executeResult = executeResultMapper.selectOne(new LambdaQueryWrapper<FunctionExecuteResult>().eq(FunctionExecuteResult::getTaskId, functionResult.getTaskId()));
            if (executeResult != null) {
                executeResultMapper.updateById(executeResult
                        .setActionApi(actionContext.getActionDetailVO().getActionApi())
                        .setActionContextInfo(jsonMapper.writeValueAsString(actionContext)));
            }
        }
        return functionResultJson;
    }


    /**
     * 对象（自定义类）参数的逐子字段绑定：按 paramPath 把多条绑定拼成字段树，
     * 由算子执行侧的组装逻辑还原成嵌套对象。
     * <p>
     * 同一参数上若还有一条不带路径的绑定，它作为整包取值，优先于逐字段组装。
     * List 入参的子字段绑定是「元素字段」，按子表行逐元素拼装，见 {@link #assembleListElements}。
     */
    private FunctionParameter assembleObjectParam(FunctionParameterVO param,
                                                  List<ActionParamMappingVO> mappings,
                                                  ActionContextInfoDTO actionContext) {
        if (FunctionParamTypeEnum.LIST.equals(param.paramTypeEnum())) {
            var elements = assembleListElements(param, mappings, actionContext);
            if (elements != null) {
                return elements;
            }
        }
        var root = new ParamFieldNode();
        mappings.stream()
                .filter(m -> StringUtils.isNotEmpty(m.getParamPath()))
                .forEach(m -> insertFieldPath(root, m));
        var fields = toFieldParams(root, param.getFields(), actionContext, param.getParamName());
        var builder = FunctionParameter.builder()
                .paramName(param.getParamName())
                .paramType(param.getParamType());
        mappings.stream()
                .filter(m -> StringUtils.isEmpty(m.getParamPath()))
                .findFirst()
                .ifPresent(m -> builder.paramValue(
                        resolveInputValue(m, param.paramTypeEnum(), actionContext, param.getParamName())));
        return builder.fields(fields.isEmpty() ? null : fields).build();
    }

    /**
     * List 入参按子表行逐元素组装：每个元素的字段各取一列取值，按行号把各列拼成一个元素，
     * 元素字段路径（如 position.longitude）在元素内逐层展开。
     * <p>只处理元素字段绑定（带 paramPath）；同一参数上还有整包绑定时返回 null，整包取值优先，与对象参数一致。
     * 某列只有单值（主表属性等实例级取值）时广播到每一行；多值列的行数必须一致，
     * 对不上说明各存储分组选的行没对齐，直接报错，见 {@link #checkListElementColumnSizes}。</p>
     */
    private FunctionParameter assembleListElements(FunctionParameterVO param,
                                                   List<ActionParamMappingVO> mappings,
                                                   ActionContextInfoDTO actionContext) {
        if (mappings.stream().anyMatch(m -> StringUtils.isEmpty(m.getParamPath()))) {
            return null;
        }
        var elementValues = elementDetailValues(actionContext, param.getParamName());
        if (elementValues != null) {
            return assembleElementsByPosition(param, mappings, actionContext, elementValues);
        }
        var columns = mappings.stream()
                .map(m -> new ListElementColumn(m, resolveInputValues(m, actionContext, param.getParamName())))
                .collect(Collectors.toList());
        int rowCount = columns.stream().mapToInt(c -> c.values == null ? 0 : c.values.size()).max().orElse(0);
        checkListElementColumnSizes(param, columns, rowCount);
        List<Object> elements = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> element = new LinkedHashMap<>();
            for (var column : columns) {
                insertElementPath(element, column.mapping.getParamPath(), columnValueAt(column.values, i));
            }
            elements.add(element);
        }
        return FunctionParameter.builder()
                .paramName(param.getParamName())
                .paramType(param.getParamType())
                .paramValue(elements)
                .build();
    }

    /**
     * 按元素给位置时逐元素组装：一个元素只取它自己那个对象的数据，元素所属对象没有绑定的字段直接不带。
     * <p>节点覆盖、固定值、外部输入这些来源不分行，按元素下标取（单值广播到每个元素）；
     * 属性来源才按元素各自的数据位置读，见 {@link #loadParamElementDetailMaps}。</p>
     */
    private FunctionParameter assembleElementsByPosition(FunctionParameterVO param,
                                                         List<ActionParamMappingVO> mappings,
                                                         ActionContextInfoDTO actionContext,
                                                         List<Map<String, Object>> elementValues) {
        List<Object> elements = new ArrayList<>(elementValues.size());
        for (int index = 0; index < elementValues.size(); index++) {
            var values = elementValues.get(index);
            Map<String, Object> element = new LinkedHashMap<>();
            for (var mapping : mappings) {
                var sharedValue = sharedSourceValue(mapping, actionContext, param.getParamName());
                if (sharedValue == null && readsInstanceRow(mapping)) {
                    if (values == null || !values.containsKey(mapping.getPropertyUniqueIdentifier())) {
                        continue;
                    }
                    insertElementPath(element, mapping.getParamPath(),
                            values.get(mapping.getPropertyUniqueIdentifier()));
                    continue;
                }
                insertElementPath(element, mapping.getParamPath(),
                        columnValueAt(asValueList(sharedValue), index));
            }
            elements.add(element);
        }
        return FunctionParameter.builder()
                .paramName(param.getParamName())
                .paramType(param.getParamType())
                .paramValue(elements)
                .build();
    }

    /** 该绑定行的来源是否需要读实例行：属性/对象/List 来源要，固定值和外部输入不要 */
    private static boolean readsInstanceRow(ActionParamMappingVO mapping) {
        var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
        return sourceType == ActionParamSourceEnum.PROPERTY || sourceType == ActionParamSourceEnum.OBJECT
                || sourceType == ActionParamSourceEnum.LIST;
    }

    /** 不按元素读实例的取值：节点覆盖优先，其次固定值、外部输入；没有这些来源时返回 null */
    private Object sharedSourceValue(ActionParamMappingVO mapping, ActionContextInfoDTO actionContext,
                                     String ownerParamName) {
        var nodeValue = paramRowOverride(actionContext, mapping, ownerParamName);
        if (nodeValue != null) {
            return nodeValue;
        }
        if (mapping.getSourceType() == ActionParamSourceEnum.FIXED) {
            return parseFixedValue(mapping.getSourceConfig());
        }
        if (mapping.getSourceType() == ActionParamSourceEnum.EXTERNAL) {
            return externalInputValue(actionContext, mapping, ownerParamName);
        }
        return null;
    }

    /** 该 LIST 入参按元素读的数据：入参名 -> 按元素顺序排列的属性取值；没按元素给位置时返回 null */
    private List<Map<String, Object>> elementDetailValues(ActionContextInfoDTO actionContext, String paramName) {
        var maps = actionContext.getParamElementDetailMaps();
        return maps == null || paramName == null ? null : maps.get(paramName);
    }

    /** List 元素的一列取值：一条元素字段绑定 + 该属性按行主键倒序的取值序列 */
    private static class ListElementColumn {
        private final ActionParamMappingVO mapping;
        private final List<Object> values;

        private ListElementColumn(ActionParamMappingVO mapping, List<Object> values) {
            this.mapping = mapping;
            this.values = values;
        }
    }

    /**
     * 多值元素字段的行数必须一致：行数对不上说明各存储分组选的行没对齐（常见于只给一个分组选了行、
     * 另一个分组还在按默认行数读），补齐 null 会多拼出空字段的元素，直接报错。
     * 单值列（主表属性等实例级取值）广播到每一行，不参与对齐。
     */
    private void checkListElementColumnSizes(FunctionParameterVO param, List<ListElementColumn> columns,
                                             int rowCount) {
        var inconsistent = columns.stream()
                .filter(column -> column.values != null && column.values.size() > 1
                        && column.values.size() != rowCount)
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(inconsistent.isEmpty(),
                "入参[" + param.getParamName() + "]的元素字段行数不一致：" + describeListColumnSizes(columns)
                        + "；同一元素的所有字段要逐行对应，多个存储分组时要同时选齐每个分组的行",
                HttpStatus.BAD_REQUEST);
    }

    private static String describeListColumnSizes(List<ListElementColumn> columns) {
        return columns.stream()
                .filter(column -> column.values != null && column.values.size() > 1)
                .map(column -> "字段[" + StringUtils.defaultIfBlank(column.mapping.getParamPath(),
                        column.mapping.getParameterName()) + "] " + column.values.size() + " 行")
                .collect(Collectors.joining("、"));
    }

    /** 第 index 个元素在该列上的取值：单值列广播到每一行，短列缺的行返回 null */
    private Object columnValueAt(List<Object> values, int index) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return index < values.size() ? values.get(index) : null;
    }

    /** 把元素字段的取值按路径写进元素；路径中间层是同名元素字段的嵌套结构 */
    @SuppressWarnings("unchecked")
    private void insertElementPath(Map<String, Object> element, String paramPath, Object value) {
        var segments = paramPath.split("\\.");
        Map<String, Object> node = element;
        for (int i = 0; i < segments.length - 1; i++) {
            var child = node.get(segments[i]);
            if (!(child instanceof Map)) {
                child = new LinkedHashMap<String, Object>();
                node.put(segments[i], child);
            }
            node = (Map<String, Object>) child;
        }
        node.put(segments[segments.length - 1], value);
    }

    /**
     * 取一个 List 入参某条元素字段绑定的全部取值（按行主键倒序），用于按行拼元素。
     * 节点覆盖、固定值、外部输入给的是单值或整包数组：数组原样用，其它包装成单元素列表由行数决定是否广播。
     */
    private List<Object> resolveInputValues(ActionParamMappingVO mapping, ActionContextInfoDTO actionContext,
                                            String ownerParamName) {
        var nodeValue = paramRowOverride(actionContext, mapping, ownerParamName);
        if (nodeValue != null) {
            return asValueList(nodeValue);
        }
        if (mapping.getSourceType() == ActionParamSourceEnum.FIXED) {
            return asValueList(parseFixedValue(mapping.getSourceConfig()));
        }
        if (mapping.getSourceType() == ActionParamSourceEnum.EXTERNAL) {
            return asValueList(externalInputValue(actionContext, mapping, ownerParamName));
        }
        var paramScopedValues = paramScopedValues(actionContext, ownerParamName, mapping.getPropertyUniqueIdentifier());
        return CollectionUtils.isNotEmpty(paramScopedValues) ? paramScopedValues
                : actionContext.getEntityDetailMap().get(mapping.getPropertyUniqueIdentifier());
    }

    /** 单值来源统一成按行的取值序列；已是数组的原样用，null 表示这一列没有值 */
    private List<Object> asValueList(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof List<?> list ? new ArrayList<>(list) : Lists.newArrayList(value);
    }

    private void insertFieldPath(ParamFieldNode root, ActionParamMappingVO mapping) {
        ParamFieldNode node = root;
        for (var segment : mapping.getParamPath().split("\\.")) {
            node = node.children.computeIfAbsent(segment, k -> new ParamFieldNode());
        }
        node.mapping = mapping;
    }

    /** 按算子声明的字段树还原各层取值；声明里已不存在的子字段跳过，不让一条失效绑定拖垮整次执行 */
    private List<FunctionParameter> toFieldParams(ParamFieldNode node, List<FunctionParameterVO> declared,
                                                  ActionContextInfoDTO actionContext, String ownerParamName) {
        List<FunctionParameter> fields = Lists.newArrayList();
        node.children.forEach((name, child) -> {
            var declaredField = declared == null ? null : declared.stream()
                    .filter(f -> name.equals(f.getParamName())).findFirst().orElse(null);
            if (declaredField == null) {
                log.warn("算子入参不存在子字段：{}，已跳过该绑定", name);
                return;
            }
            var children = toFieldParams(child, declaredField.getFields(), actionContext, ownerParamName);
            var builder = FunctionParameter.builder()
                    .paramName(name)
                    .paramType(declaredField.getParamType());
            if (child.mapping != null) {
                builder.paramValue(resolveInputValue(child.mapping, declaredField.paramTypeEnum(), actionContext,
                        ownerParamName));
            }
            if (!children.isEmpty()) {
                builder.fields(children);
            }
            fields.add(builder.build());
        });
        return fields;
    }

    /** 子字段绑定树：中间节点只有 children，叶子节点记一条绑定 */
    private static class ParamFieldNode {
        private final Map<String, ParamFieldNode> children = new LinkedHashMap<>();
        private ActionParamMappingVO mapping;
    }


    /**
     * 取一个入参的实参值：固定值取行为定义里的字面量，外部输入取行为测试时传入的值，
     * 其余来源从实体属性详情取。
     * <p>ownerParamName 是该绑定行所属的顶层算子入参名：调用方按入参指定过实例时先取该入参自己的视图，
     * 这样同一对象的两个属性入参可以各读一个实例；视图里没有该属性时回落到共用视图。</p>
     */
    private Object resolveInputValue(ActionParamMappingVO mapping, FunctionParamTypeEnum paramType,
                                     ActionContextInfoDTO actionContext, String ownerParamName) {
        // 行为节点给这个入参配了取值来源（黑板变量/上游行为出参）时按节点给的算，属性、固定值来源也一样被覆盖
        var nodeValue = paramRowOverride(actionContext, mapping, ownerParamName);
        if (nodeValue != null) {
            return nodeValue;
        }
        if (mapping.getSourceType() == ActionParamSourceEnum.FIXED) {
            return parseFixedValue(mapping.getSourceConfig());
        }
        if (mapping.getSourceType() == ActionParamSourceEnum.EXTERNAL) {
            return externalInputValue(actionContext, mapping, ownerParamName);
        }
        var paramScopedValues = paramScopedValues(actionContext, ownerParamName, mapping.getPropertyUniqueIdentifier());
        // 如果对象没有给属性绑定数据源，则获取的values为null，函数可能执行失败
        List<Object> values = CollectionUtils.isNotEmpty(paramScopedValues) ? paramScopedValues
                : actionContext.getEntityDetailMap().get(mapping.getPropertyUniqueIdentifier());
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        return FunctionParamTypeEnum.LIST.equals(paramType) ? values : values.get(0);
    }

    /** 行为树节点为该入参配的取值（黑板变量或上游行为出参）；非树执行或没配时返回 null，按绑定行自己的来源取 */
    private Object paramValueOverride(ActionContextInfoDTO actionContext, String paramName) {
        var overrides = actionContext.getParamValueOverrides();
        if (MapUtils.isEmpty(overrides) || StringUtils.isBlank(paramName)) {
            return null;
        }
        return overrides.get(paramName);
    }

    /**
     * 绑定行的节点取值：先按「父参数名.字段路径」找，没配再按绑定行自己的名字。
     * 两条入参有同名叶子（两条轨迹都有 longitude）时只有限定写法能分得开；
     * 没重名的绑定仍按叶子名命中，老配置不受影响。
     * 裸名命中且该名字同时挂在多条路径不同的绑定行上时直接报错：
     * 行为后来加了同名叶子会让老绑定一份覆盖值静默写进两条入参。
     */
    private Object paramRowOverride(ActionContextInfoDTO actionContext, ActionParamMappingVO mapping,
                                    String ownerParamName) {
        if (mapping == null) {
            return paramValueOverride(actionContext, ownerParamName);
        }
        if (StringUtils.isNotBlank(mapping.getParamPath()) && StringUtils.isNotBlank(ownerParamName)) {
            var qualified = paramValueOverride(actionContext, ownerParamName + "." + mapping.getParamPath());
            if (qualified != null) {
                return qualified;
            }
        }
        var overrideKey = StringUtils.isNotBlank(mapping.getParameterName())
                ? mapping.getParameterName() : ownerParamName;
        var bare = paramValueOverride(actionContext, overrideKey);
        if (bare != null && StringUtils.isNotBlank(mapping.getParameterName())
                && hasConflictingSameNameBindings(actionContext, mapping)) {
            throw new BusinessException("节点入参覆盖 [" + overrideKey + "] 命中了多条同名但路径不同的绑定行，"
                    + "无法确定这个值属于哪个参数，请在行为节点重新选择入参（按 父参数名.字段路径 全名绑定）",
                    HttpStatus.BAD_REQUEST);
        }
        return bare;
    }

    /** 同名裸键是否还有路径/属性指向不同的其它输入绑定行（完全重复的旧行不算冲突，维持原覆盖口径） */
    private boolean hasConflictingSameNameBindings(ActionContextInfoDTO actionContext, ActionParamMappingVO mapping) {
        if (CollectionUtils.isEmpty(actionContext.getMappings())) {
            return false;
        }
        return actionContext.getMappings().stream()
                .filter(m -> m != null && m.getCategory() != FunctionParamCategoryEnum.OUTPUT)
                .filter(m -> StringUtils.isNotBlank(m.getParameterName())
                        && m.getParameterName().equals(mapping.getParameterName()))
                .anyMatch(m -> !Objects.equals(m.getParamPath(), mapping.getParamPath())
                        || !Objects.equals(m.getPropertyUniqueIdentifier(), mapping.getPropertyUniqueIdentifier()));
    }

    /** 该入参按 inputParams 单独加载的视图里此属性的取值；没指定过这个入参或视图里没有该属性时返回 null，由调用处回落共用视图 */
    private List<Object> paramScopedValues(ActionContextInfoDTO actionContext, String ownerParamName,
                                           String propertyUniqueIdentifier) {
        var paramMaps = actionContext.getParamEntityDetailMaps();
        if (paramMaps == null || ownerParamName == null || propertyUniqueIdentifier == null) {
            return null;
        }
        var scoped = paramMaps.get(ownerParamName);
        return scoped == null ? null : scoped.get(propertyUniqueIdentifier);
    }

    /**
     * 外部输入的取值：先按「父参数名.字段路径」的全名键取，再按绑定行的参数名取，其次按子字段路径取，
     * 最后按整包参数名取。
     * <p>整包绑定的参数名就是算子入参名；老数据按子字段逐行绑定外部输入时，参数名是字段名
     * （列表元素的 latitude、longitude 各算一行），这里按路径和所属入参回退，
     * 请求按「参数名 -&gt; 对象/数组」整包给值时也能取到对应字段。键存在就按显式值返回（null 也算给了值）。</p>
     * <p>裸字段名同时挂在多条指向不同字段的外部输入绑定行上时直接报错：一个值喂两条入参会静默串值，
     * 必须让调用方用 {@code tracks.time} 这样的全名键指明给谁。</p>
     */
    private Object externalInputValue(ActionContextInfoDTO actionContext, ActionParamMappingVO mapping,
                                      String ownerParamName) {
        var externalParams = actionContext.getExternalParams();
        if (MapUtils.isEmpty(externalParams)) {
            return null;
        }
        var paramPath = mapping.getParamPath();
        if (StringUtils.isNotEmpty(ownerParamName) && StringUtils.isNotEmpty(paramPath)) {
            var qualifiedKey = ownerParamName + "." + paramPath;
            if (externalParams.containsKey(qualifiedKey)) {
                return externalParams.get(qualifiedKey);
            }
        }
        var parameterName = mapping.getParameterName();
        if (StringUtils.isNotEmpty(parameterName) && externalParams.containsKey(parameterName)) {
            PreconditionUtils.checkArgument(!hasConflictingExternalBindings(actionContext, mapping),
                    "外部输入[" + parameterName + "]同时是几条入参上的字段名，params 里给一个值会同时喂给它们："
                            + "请改按「父参数名.字段路径」传（如 "
                            + StringUtils.defaultIfBlank(ownerParamName, "父参数名") + "." + parameterName
                            + "），或按顶层入参名整包给值", HttpStatus.BAD_REQUEST);
            return externalParams.get(parameterName);
        }
        if (StringUtils.isNotEmpty(paramPath) && externalParams.containsKey(paramPath)) {
            return externalParams.get(paramPath);
        }
        if (StringUtils.isEmpty(ownerParamName) || !externalParams.containsKey(ownerParamName)) {
            return null;
        }
        var wholeValue = externalParams.get(ownerParamName);
        if (StringUtils.isEmpty(paramPath)) {
            return wholeValue;
        }
        Object node = wholeValue;
        for (var segment : paramPath.split("\\.")) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = map.get(segment);
        }
        return node;
    }

    /** 同名裸键是否还挂在其它指向不同字段的外部输入绑定行上（完全重复的旧行不算冲突） */
    private boolean hasConflictingExternalBindings(ActionContextInfoDTO actionContext,
                                                   ActionParamMappingVO mapping) {
        if (CollectionUtils.isEmpty(actionContext.getMappings())) {
            return false;
        }
        return actionContext.getMappings().stream()
                .filter(m -> m != null && m.getCategory() != FunctionParamCategoryEnum.OUTPUT)
                .filter(m -> m.getSourceType() == ActionParamSourceEnum.EXTERNAL)
                .filter(m -> StringUtils.isNotBlank(m.getParameterName())
                        && m.getParameterName().equals(mapping.getParameterName()))
                .anyMatch(m -> !Objects.equals(m.getParamPath(), mapping.getParamPath())
                        || !Objects.equals(m.getPropertyUniqueIdentifier(), mapping.getPropertyUniqueIdentifier()));
    }

    /** 固定值按完整 JSON 字面量解析，整串不是合法 JSON 时按原文本返回 */
    private Object parseFixedValue(String sourceConfig) {
        if (StringUtils.isEmpty(sourceConfig)) {
            return null;
        }
        try {
            return fixedValueMapper.readValue(sourceConfig, Object.class);
        } catch (Exception e) {
            return sourceConfig;
        }
    }


    @SneakyThrows
    @Override
    public void updateEntityPropertyAndRelation(String jsonString, ActionContextInfoDTO actionContext) {
        // 按行级 outputMode 回写：只有标记为 WRITE_PROPERTY 的出参行才写回属性
        writeFunctionOutputToProperties(jsonString, actionContext);
        // 关系边只有应用行为才落，基础行为（含基础算子）不写库
        if (actionContext.getActionDetailVO().getActionKind() != ActionKindEnum.BASE) {
            updateEntityRelationByFunction(jsonString, actionContext);
        }
    }

    /**
     * 按出参绑定把算子输出写回对象属性：主表列走 update，分组表补一行 insert。
     * <p>写回按「对象 + 实例主键」分组，不是只按对象：同一对象的两个出参各写一个实例时（outputParams）
     * 会分成两组，各自用自己的主键更新，不会把两行的值合并写到同一个实例。</p>
     */
    private void writeFunctionOutputToProperties(String jsonString, ActionContextInfoDTO actionContext) {
        //更新实体属性值
        //根据函数的输出结果更新源实体属性：通过json path获取value
        // 算子可以有多个出参，绑定了哪几个就回写哪几个；没填取值路径的绑定跳过，它本身无法定位出参
        PreconditionUtils.checkNotNull(actionContext.getFunctionDetailVO(),
                "行为没有绑定算子，无法按出参回写属性", HttpStatus.BAD_REQUEST);
        var functionOutputParams = functionParamsOf(actionContext, FunctionParamCategoryEnum.OUTPUT);
        var outputParamIds = functionOutputParams.stream().map(FunctionParameterVO::getParamId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        // 按行级 outputMode 过滤：只有标记 WRITE_PROPERTY 的出参行才回写
        var writeRows = actionContext.getMappings().stream()
                .filter(m -> m.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY)
                .filter(m -> StringUtils.isNotEmpty(m.getFunctionParamExpression()))
                .collect(Collectors.toList());
        var output = writeRows.stream()
                .filter(m -> m.getFunctionParamId() != null && outputParamIds.contains(m.getFunctionParamId()))
                .collect(Collectors.toList());
        if (output.isEmpty()) {
            // 没有写属性行：仅返回结果的行为，正常跳过
            if (writeRows.isEmpty()) {
                return;
            }
            // 有写属性行却一条都对不上当前出参：算子重新保存会重建 function_param 主键，绑定行整体失效。
            // 静默跳过会被当成已经写回，必须报错让人重新绑定出参
            throw new BusinessException("出参写回失败：" + writeRows.size()
                    + " 条写属性绑定已不指向算子当前出参（算子可能被重新保存过、参数已重建），请重新绑定出参",
                    HttpStatus.BAD_REQUEST);
        }
        // 出参行可以指向不同对象：属性表按对象合并，回写时各用自己的主键
        var propMap = new LinkedHashMap<String, OntologyProperty>();
        if (CollectionUtils.isNotEmpty(actionContext.getOntologyProperties())) {
            actionContext.getOntologyProperties().forEach(p -> propMap.putIfAbsent(p.getUniqueIdentifier(), p));
        }
        if (MapUtils.isNotEmpty(actionContext.getObjectProperties())) {
            actionContext.getObjectProperties().values()
                    .forEach(list -> list.forEach(p -> propMap.putIfAbsent(p.getUniqueIdentifier(), p)));
        }
        var primaryPropByObject = propMap.values().stream()
                .filter(v -> v.getIsPrimaryKey() == 1 && StringUtils.isNotEmpty(v.getOntologyUniqueIdentifier()))
                .collect(Collectors.toMap(OntologyProperty::getOntologyUniqueIdentifier, v -> v, (a, b) -> a));
        var param = actionContext.getEntityActionExecuteParam();
        Map<WriteTarget, List<EntityUpdateParam.ColumnUpdate>> columnUpdatesByTarget = new LinkedHashMap<>();
        Map<WriteTarget, Map<String, Map<String, Object>>> insertMap = new LinkedHashMap<>();
        // 指定了分组行主键的出参：写回目标 -> 分组表 -> (行主键 + 待更新列)，按行改而不是插新行
        Map<WriteTarget, Map<String, RowUpdate>> updateRowMap = new LinkedHashMap<>();
        // 取值路径含 [*] 的出参：按「写回目标 + 分组表 + 循环数组」攒成一组，最后逐元素 zip 成多行插子表
        Map<WildcardKey, List<WildcardColumn>> wildcardGroups = new LinkedHashMap<>();
        // 通配列共用一份解析结果：同一返回体按列各解析一次，列多、元素多时是纯重复开销
        var wildcardRoot = output.stream()
                .anyMatch(out -> FunctionOutputValueReader.hasWildcard(out.getFunctionParamExpression()))
                ? FunctionOutputValueReader.parse(jsonString) : null;
        output.stream().forEach(out -> {
            var prop = propMap.get(out.getPropertyUniqueIdentifier());
            if (prop == null) {
                log.warn("行为写回属性不存在，跳过：{}", out.getPropertyUniqueIdentifier());
                return;
            }
            var outParamName = paramNameOf(functionOutputParams, out.getFunctionParamId());
            // 通配出参不走向单值的 read：数组取到单个值时形状必然不匹配，会被当成「取不到值」报错
            var wildcard = FunctionOutputValueReader.hasWildcard(out.getFunctionParamExpression());
            Object actualValue = wildcard ? null
                    : convertWriteValue(FunctionOutputValueReader.read(jsonString, out.getFunctionParamExpression(),
                            outParamName, prop.getPropertyType()), prop);
            var objectId = StringUtils.isNotEmpty(out.getOntologyUniqueIdentifier())
                    ? out.getOntologyUniqueIdentifier() : prop.getOntologyUniqueIdentifier();
            var primaryProp = primaryPropByObject.get(objectId);
            PreconditionUtils.checkNotNull(primaryProp,
                    "对象[" + objectId + "]未配置主键属性，无法写回", HttpStatus.BAD_REQUEST);
            var entityPrimaryKey = datalakeKey(primaryProp.getDatasourceSchema(), primaryProp.getDatasourceId(),
                    primaryProp.getDatasourceColumnName(), resolveOutputPrimaryKey(actionContext, param, out, objectId));
            PreconditionUtils.checkNotNull(entityPrimaryKey,
                    "对象[" + objectId + "]未选择实例数据，无法写回", HttpStatus.BAD_REQUEST);
            var writeTarget = new WriteTarget(objectId, entityPrimaryKey);
            var toMainTable = prop.getDatasourceId().equals(primaryProp.getDatasourceId());
            var rowKey = toMainTable ? null
                    : outputRowKeyFor(actionContext, out, objectId, prop.getStorageGroup());
            // 通配出参：一列取一批值，先攒着，最后和同组其它列逐元素成行插子表
            if (wildcard) {
                wildcardGroups.computeIfAbsent(new WildcardKey(writeTarget, prop.getDatasourceId(),
                                FunctionOutputValueReader.loopPrefix(out.getFunctionParamExpression())),
                        ignored -> new ArrayList<>())
                        .add(new WildcardColumn(prop.getDatasourceColumnName(), prop.getDisplayName(),
                                readWildcardValues(wildcardRoot, out, outParamName, prop, toMainTable, rowKey)));
                return;
            }
            // 取不到值：改已有的行必须报错，用 null 会把原值抹掉；新增子表行没有原值，按空值补齐继续插
            if (actualValue == null) {
                PreconditionUtils.checkArgument(!toMainTable && rowKey == null,
                        "出参回写取不到值：属性[" + prop.getDisplayName()
                                + "]的取值路径 " + out.getFunctionParamExpression()
                                + " 在算子返回结果里不存在或值为 null", HttpStatus.BAD_REQUEST);
            }
            //主数据源列
            if (toMainTable) {
                columnUpdatesByTarget.computeIfAbsent(writeTarget, key -> Lists.newArrayList())
                        .add(EntityUpdateParam.ColumnUpdate.builder()
                                .datasourceColumnName(prop.getDatasourceColumnName())
                                .columnValue(actualValue)
                                .propertyUniqIdentifier(out.getPropertyUniqueIdentifier())
                                .build());
            } else {
                if (rowKey != null) {
                    updateRowMap.computeIfAbsent(writeTarget, key -> new LinkedHashMap<>())
                            .computeIfAbsent(prop.getDatasourceId(), key -> new RowUpdate(rowKey))
                            .columns().put(prop.getDatasourceColumnName(), actualValue);
                } else {
                    insertMap.computeIfAbsent(writeTarget, key -> new LinkedHashMap<>())
                            .computeIfAbsent(prop.getDatasourceId(), key -> new HashMap<>())
                            .put(prop.getDatasourceColumnName(), actualValue);
                }
            }

        });
        //主数据源数据update：逐写回目标用自己的主键
        columnUpdatesByTarget.forEach((target, columnUpdates) -> {
            var primaryProp = primaryPropByObject.get(target.objectId());
            updateProperty(EntityUpdateParam.builder()
                    .columnUpdates(columnUpdates)
                    .primaryKeyValue(target.entityPrimaryKey())
                    .schemaName(primaryProp.getDatasourceSchema())
                    .datasourceId(primaryProp.getDatasourceId())
                    .build());
        });
        //其他数据源按行更新：先确认该行属于本实例，找不到就报错，不退回插新行
        if (MapUtils.isNotEmpty(updateRowMap)) {
            updateRowMap.forEach((target, byDatasource) -> {
                var primaryProp = primaryPropByObject.get(target.objectId());
                var entityPrimaryKey = target.entityPrimaryKey();
                byDatasource.forEach((datasourceId, update) -> {
                    var schema = primaryProp.getDatasourceSchema();
                    var mapping = tableFieldMappingMapper.selectByTargetTable(schema, datasourceId);
                    PreconditionUtils.checkNotNull(mapping,
                            "分组表未配置关联列，无法按行写回：" + datasourceId, HttpStatus.BAD_REQUEST);
                    var rowKeyColumn = tableMetadataMapper.queryPrimaryKeyColumnName(schema, datasourceId);
                    var rowKeyValue = datalakeKey(schema, datasourceId, rowKeyColumn, update.rowKey());
                    var existing = objectMapper.queryJoinTableRow(schema, entityPrimaryKey, datasourceId,
                            Lists.newArrayList(rowKeyColumn), mapping.getTargetColumnName(),
                            rowKeyColumn, rowKeyValue);
                    PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(existing),
                            "分组表[" + datasourceId + "]中行主键 " + update.rowKey()
                                    + " 不存在或不属于实体 " + entityPrimaryKey + "，无法按行写回",
                            HttpStatus.BAD_REQUEST);
                    List<EntityUpdateParam.ColumnUpdate> columnUpdates = update.columns().entrySet().stream()
                            .<EntityUpdateParam.ColumnUpdate>map(entry -> EntityUpdateParam.ColumnUpdate.builder()
                                    .propertyUniqIdentifier(resolvePropertyUniqIdentifier(propMap, datasourceId, entry.getKey()))
                                    .datasourceColumnName(entry.getKey())
                                    .columnValue(entry.getValue())
                                    .build())
                            .collect(Collectors.toList());
                    updateProperty(EntityUpdateParam.builder()
                            .schemaName(schema)
                            .datasourceId(datasourceId)
                            .primaryKeyValue(update.rowKey())
                            .columnUpdates(columnUpdates)
                            .build());
                });
            });
        }
        //其他数据源数据insert：逐写回目标用自己的主键与主键表结构
        if (MapUtils.isNotEmpty(insertMap)) {
            insertMap.forEach((target, byDatasource) -> {
                var primaryProp = primaryPropByObject.get(target.objectId());
                var primaryKey = target.entityPrimaryKey();
                byDatasource.forEach((tableName, columns) -> {
                    //查询关联建，补全columns
                    var mapping = tableFieldMappingMapper.selectByTargetTable(primaryProp.getDatasourceSchema(), tableName);
                    PreconditionUtils.checkNotNull(mapping,
                            "分组表未配置关联列，无法插新行：" + tableName, HttpStatus.BAD_REQUEST);
                    columns.put(mapping.getTargetColumnName(), primaryKey);
                    fillUnwrittenColumns(primaryProp.getDatasourceSchema(), target.objectId(), tableName, columns, propMap);
                    objectMapper.insertObject(primaryProp.getDatasourceSchema(), tableName, columns);
                });
            });
        }
        insertWildcardRows(wildcardGroups, primaryPropByObject, propMap);
    }

    /**
     * 通配出参取一批值：路径里的 {@code [*]} 表示对数组逐元素取同一列。
     * <p>主表只有实例那一行、按分组行主键是改已有行，两者都和「逐元素插新行」冲突，先拦住；
     * 取不到数组、整批全是 null（元素里没有这个字段）、元素形状与目标属性类型不匹配，都在落库前报错。
     * 取到的值按属性类型转换（JSON 里的数字统一是 Integer/Double，列要求 int4/float4 时直接插会报类型不符）。</p>
     */
    private List<Object> readWildcardValues(JsonNode root, ActionParamMappingVO out, String outParamName,
                                            OntologyProperty prop, boolean toMainTable, Object rowKey) {
        PreconditionUtils.checkArgument(!toMainTable,
                "取值路径带 [*] 的出参只能写子表，属性[" + prop.getDisplayName() + "]在主表上", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(rowKey == null,
                "取值路径带 [*] 的出参按元素新增多行，不能同时指定分组表行主键：属性[" + prop.getDisplayName() + "]",
                HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkNotNull(root,
                "通配出参取不到数组：算子返回结果不是合法 JSON，属性[" + prop.getDisplayName() + "]",
                HttpStatus.BAD_REQUEST);
        var values = FunctionOutputValueReader.readMany(root, out.getFunctionParamExpression(), outParamName);
        PreconditionUtils.checkArgument(values != null,
                "通配出参取不到数组：属性[" + prop.getDisplayName() + "]的取值路径 "
                        + out.getFunctionParamExpression() + " 在算子返回结果里定位不到数组", HttpStatus.BAD_REQUEST);
        // 空数组是合法结果（这个时间段没有数据）：本次不插行，由落库侧 warn 后跳过，不当成形状错误
        if (values.isEmpty()) {
            return values;
        }
        PreconditionUtils.checkArgument(values.stream().anyMatch(Objects::nonNull),
                "通配出参取不到值：属性[" + prop.getDisplayName() + "]的取值路径 "
                        + out.getFunctionParamExpression() + " 在数组元素里没有对应字段", HttpStatus.BAD_REQUEST);
        var sample = values.stream().filter(Objects::nonNull).findFirst().orElse(null);
        PreconditionUtils.checkArgument(FunctionOutputValueReader.shapeMatches(sample, prop.getPropertyType()),
                "通配出参的元素形状与属性类型不匹配：属性[" + prop.getDisplayName() + "]取不到标量值，"
                        + "请把取值路径写到元素内的具体字段（如 " + out.getFunctionParamExpression() + ".字段名）",
                HttpStatus.BAD_REQUEST);
        return convertWildcardValues(values, prop);
    }

    /** 按属性类型转换整列取值：转换不了的元素直接报错，带上元素序号与属性名，避免插库时才抛类型不符 */
    private List<Object> convertWildcardValues(List<Object> values, OntologyProperty prop) {
        if (prop.getPropertyType() == null) {
            return values;
        }
        List<Object> converted = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value == null) {
                converted.add(null);
                continue;
            }
            try {
                converted.add(convertWriteValue(value, prop));
            } catch (RuntimeException e) {
                throw new BusinessException("通配出参第 " + (i + 1) + " 个元素的值[" + value
                        + "]无法转换为属性[" + prop.getDisplayName() + "]的类型 " + prop.getPropertyType(),
                        HttpStatus.BAD_REQUEST);
            }
        }
        return converted;
    }

    /**
     * 出参写回的值按目标属性的数据类型转换后再落库。
     * <p>取值只负责从返回体里拿到值（JSON 数字统一是 Integer/Double、时间和布尔常被脚本写成字符串），
     * 原样进 SQL 会在插库时抛「column "time" is of type timestamp but expression is of type character varying」，
     * 这条异常又被按对端逐条执行的 try 吞成空结果，看上去就成了「算子没有返回结果」。通配与单值两条写回共用这里。
     * 时间、整型、长整型、浮点、双精度、Decimal、布尔按类型转；其余类型（对象、List、Map 等）原样透传，与改造前一致。</p>
     * <p>数字值按它的文本形态走 {@link #convertByTargetType}：同一列用字符串写和用数字写必须同口径，
     * 过去 {@code number.intValue()} 会把 500.9 静默截成 500、{@code 1} 进 boolean 列原样传下去留给插库报错。</p>
     */
    @Override
    public Object convertWriteValue(Object value, OntologyProperty prop) {
        if (value == null || prop.getPropertyType() == null) {
            return value;
        }
        var type = prop.getPropertyType();
        if (value instanceof Number number) {
            if (type == OntologyDataTypeEnum.String) {
                return number.toString();
            }
            return isConvertibleTargetType(type) ? convertByTargetType(number.toString(), type, prop) : number;
        }
        return convertByTargetType(value, type, prop);
    }

    /** 有明确转换语义的列类型；结构类（对象、List、Map 等）没有标量形态，原样透传 */
    private boolean isConvertibleTargetType(OntologyDataTypeEnum type) {
        return switch (type) {
            case Timestamp, Date, Float, Double, Decimal, Int, Long, Bool -> true;
            default -> false;
        };
    }

    /**
     * 按目标属性类型转换，转不了时报出值和属性名，不让 NumberFormatException 冒充「算子没有返回结果」。
     * <p>字符串值补齐三种列：timestamp/date 走 {@link DataConverter} 的多格式解析；
     * 数值列（Int/Long/Decimal）接受小数写法，脚本把数量写成 "500.0" 很常见，
     * 直接 {@code Integer.parseInt} 会拒；布尔列必须换成 Boolean，
     * 字符串进 boolean 列和进 timestamp 列是同一类错。</p>
     */
    private Object convertByTargetType(Object value, OntologyDataTypeEnum type, OntologyProperty prop) {
        if (!isConvertibleTargetType(type)) {
            return value;
        }
        try {
            if (value instanceof String text) {
                var trimmed = text.trim();
                switch (type) {
                    case Bool -> {
                        return switch (trimmed.toLowerCase()) {
                            case "true", "1", "t", "yes" -> Boolean.TRUE;
                            case "false", "0", "f", "no" -> Boolean.FALSE;
                            default -> throw new IllegalArgumentException("不是布尔取值");
                        };
                    }
                    case Decimal -> {
                        return new BigDecimal(trimmed);
                    }
                    // 小数形式的整数写法（"500.0"）按整数收下，带真实小数位的不猜、报错
                    case Int -> {
                        return new BigDecimal(trimmed).intValueExact();
                    }
                    case Long -> {
                        return new BigDecimal(trimmed).longValueExact();
                    }
                    default -> {
                        return DataConverter.convert2DataType(value, type);
                    }
                }
            }
            return DataConverter.convert2DataType(value, type);
        } catch (RuntimeException e) {
            throw new BusinessException("出参写回的值[" + value + "]无法转换为属性["
                    + prop.getDisplayName() + "]的类型 " + type + "，请检查该属性的取值路径", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 通配出参落库：同一「写回目标 + 分组表 + 循环数组」的列逐元素 zip 成行，追加插入子表。
     * <p>各列长度必须一致：不一致说明有列的路径写错，截到最短会留下每行的列来自不同元素的错位行，
     * 所以整组一行都不插。行数按 {@value #MAX_WILDCARD_OUTPUT_ROWS} 封顶、每批行数按分组表列数收敛
     * （见 {@link #wildcardInsertBatchSize(int)}），只插不删，重复执行按追加口径留多份。</p>
     */
    private void insertWildcardRows(Map<WildcardKey, List<WildcardColumn>> groups,
                                    Map<String, OntologyProperty> primaryPropByObject,
                                    Map<String, OntologyProperty> propMap) {
        groups.forEach((key, columns) -> {
            var primaryProp = primaryPropByObject.get(key.target().objectId());
            var schema = primaryProp.getDatasourceSchema();
            var tableName = key.tableName();
            var mapping = tableFieldMappingMapper.selectByTargetTable(schema, tableName);
            PreconditionUtils.checkNotNull(mapping,
                    "分组表未配置关联列，通配出参无法批量写回：" + tableName, HttpStatus.BAD_REQUEST);
            var sizes = columns.stream().map(column -> column.values().size()).distinct().toList();
            PreconditionUtils.checkArgument(sizes.size() == 1,
                    "通配出参各列取到的长度不一致，无法逐元素成行：" + describeWildcardColumns(columns)
                            + "，请检查这些列的取值路径是否都指向同一个数组", HttpStatus.BAD_REQUEST);
            var rows = sizes.get(0);
            PreconditionUtils.checkArgument(rows <= MAX_WILDCARD_OUTPUT_ROWS,
                    "通配出参一次写回 " + rows + " 行，超过上限 " + MAX_WILDCARD_OUTPUT_ROWS
                            + " 行，请缩小算子的计算范围或分多次执行", HttpStatus.BAD_REQUEST);
            if (rows == 0) {
                log.warn("通配出参数组为空，本次不写行：对象={}, 实例={}, 表={}, 数组路径={}",
                        key.target().objectId(), key.target().entityPrimaryKey(), tableName, key.loopPrefix());
                return;
            }
            // 该分组表里没绑定的列先补 null，再逐元素覆盖已绑定列，保证插出来的行列齐全
            var template = new LinkedHashMap<String, Object>();
            fillUnwrittenColumns(schema, key.target().objectId(), tableName, template, propMap);
            // 一批拼一条 INSERT，占位符数 = 行数 × 每行列数（外键列可能不在模板里，按 +1 取上限）
            var batchSize = wildcardInsertBatchSize(template.size() + 1);
            var batch = new ArrayList<Map<String, Object>>(batchSize);
            for (int i = 0; i < rows; i++) {
                var row = new LinkedHashMap<String, Object>(template);
                for (var column : columns) {
                    row.put(column.columnName(), column.values().get(i));
                }
                row.put(mapping.getTargetColumnName(), key.target().entityPrimaryKey());
                batch.add(row);
                if (batch.size() == batchSize) {
                    objectMapper.batchInsertObject(schema, tableName, batch);
                    batch = new ArrayList<>(batchSize);
                }
            }
            if (!batch.isEmpty()) {
                objectMapper.batchInsertObject(schema, tableName, batch);
            }
        });
    }

    /**
     * 按列数收敛每批行数：PostgreSQL 单条语句最多 65535 个绑定参数，超了直接报「too many parameters」，
     * 而前几批已经落库、只剩半批数据。列少的表仍按 {@value #WILDCARD_INSERT_BATCH} 行走。
     */
    private int wildcardInsertBatchSize(int columnCount) {
        if (columnCount <= 0) {
            return WILDCARD_INSERT_BATCH;
        }
        return Math.max(1, Math.min(WILDCARD_INSERT_BATCH, MAX_BIND_PARAMS_PER_STATEMENT / columnCount));
    }

    /** 各列的取值条数，报错时用来指出是哪一列的路径短了 */
    private String describeWildcardColumns(List<WildcardColumn> columns) {
        return columns.stream()
                .map(column -> column.displayName() + "=" + column.values().size() + "条")
                .collect(Collectors.joining("、"));
    }

    /** 通配出参的分组键：写回目标 + 分组表 + 循环数组路径，同组内的列逐元素成行 */
    private record WildcardKey(WriteTarget target, String tableName, String loopPrefix) {
    }

    /** 通配出参里的一列：列名、报错用的属性显示名、以及每个元素的取值 */
    private record WildcardColumn(String columnName, String displayName, List<Object> values) {
    }

    /**
     * 子表新增行的兜底补列：这个对象在该分组表里声明过、本次没有值的属性列补空，保证新增的行列齐全。
     * <p>分组表自己的主键由表上的序列生成，补空会撞主键，要按真实列名排除；
     * 匹配按「对象 + 表」精确来，不假设同名表在不同对象间可共用。</p>
     */
    private void fillUnwrittenColumns(String schema, String objectId, String tableName, Map<String, Object> columns,
                                      Map<String, OntologyProperty> propMap) {
        var rowKeyColumn = tableMetadataMapper.queryPrimaryKeyColumnName(schema, tableName);
        propMap.values().stream()
                .filter(prop -> Objects.equals(prop.getOntologyUniqueIdentifier(), objectId)
                        && Objects.equals(prop.getDatasourceId(), tableName))
                .filter(prop -> !Objects.equals(prop.getDatasourceColumnName(), rowKeyColumn))
                .map(OntologyProperty::getDatasourceColumnName)
                .filter(StringUtils::isNotEmpty)
                .forEach(columnName -> columns.putIfAbsent(columnName, null));
    }

    /** 一次写回的目标：对象 + 本次落到该对象的实例主键；同一对象的不同出参可以写到不同实例 */
    private record WriteTarget(String objectId, Object entityPrimaryKey) {
    }

    /** 按行写回的待更新数据：行主键 + 该行要改的列 */
    private record RowUpdate(Object rowKey, Map<String, Object> columns) {
        private RowUpdate(Object rowKey) {
            this(rowKey, new LinkedHashMap<>());
        }
    }

    /** 回写列对应的属性标识；列名是唯一依据，找不到属性时用列名兜底（内部调用不做必填校验） */
    private String resolvePropertyUniqIdentifier(Map<String, OntologyProperty> propMap, String datasourceId,
                                                 String datasourceColumnName) {
        return propMap.values().stream()
                .filter(p -> Objects.equals(p.getDatasourceId(), datasourceId)
                        && Objects.equals(p.getDatasourceColumnName(), datasourceColumnName))
                .map(OntologyProperty::getUniqueIdentifier)
                .findFirst()
                .orElse(datasourceColumnName);
    }

    /**
     * 按算子结果落关系边：算子在 {@link FunctionResultVO} 里返回可见窗口（startTime + endTime）即这条关系成立，
     * 算子没返回顶层窗口时依次回落到算子的可见窗口列表、行为定义里固定的持续时段，三者都取不到才跳过并记 warn。
     * <p>
     * 不建也不删：一个只想回写属性的行为不能顺带把图库里已有的边置为删除；
     * 边的启停由实例关系行为负责，它按人工指定的目标状态改边，不走算子结果。
     */
    @SneakyThrows
    private void updateEntityRelationByFunction(String jsonString, ActionContextInfoDTO actionContext) {
        if (actionContext.getLink() == null) {
            return;
        }
        FunctionResultVO resultVO = readFunctionResult(jsonString);
        if (resultVO == null) {
            return;
        }
        Date startTime = resultVO.getStartTime();
        Date endTime = resultVO.getEndTime();
        List<VisibilityWindow> timeWindows = resultVO.getTimeWindows();
        // 一级起止缺失时逐级回落：算子返回的窗口列表 → 行为定义里固定的持续时段；算子返回了顶层起止的旧路径取值完全不变
        if (startTime == null || endTime == null) {
            var nearest = nearestWindow(timeWindows);
            if (nearest == null) {
                var definedWindows = actionContext.getLink().getVisibilityWindows();
                nearest = nearestWindow(definedWindows);
                if (nearest != null) {
                    timeWindows = definedWindows;
                }
            }
            if (nearest != null) {
                startTime = nearest.getStartTime();
                endTime = nearest.getEndTime();
            }
        }
        if (startTime == null || endTime == null) {
            log.warn("行为 {} 实例关系边落位跳过：算子既没返回顶层起止也没返回可见窗口，行为定义没有可用的持续时段，link={}",
                    actionContext.getActionDetailVO().getActionApi(),
                    actionContext.getLink().getOntologyLinkUniqIdentifier());
            return;
        }

        //根据函数的输出结果更新实体关系
        var linkGroupId = actionContext.getLink().getOntologyLinkUniqIdentifier();
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, linkGroupId));
        PreconditionUtils.checkNotNull(link, "无效的对象关系：" + linkGroupId, HttpStatus.BAD_REQUEST);
        // 调用方按对象把两端实例都给了时直接按关系定义的 From/To 用，不再从行为所属对象推方向；
        // 批量/调度只给本端（对端逐个遍历）时沿用原推算
        var explicitFrom = endpointPrimaryKey(actionContext, link.getOntologyUniqueIdentifierFrom());
        // 自关系两端同为对象 id，map 只放本端，对端取本次遍历到的实例
        var explicitTo = StringUtils.equals(link.getOntologyUniqueIdentifierFrom(), link.getOntologyUniqueIdentifierTo())
                ? actionContext.getLinkEntityPrimaryKey()
                : endpointPrimaryKey(actionContext, link.getOntologyUniqueIdentifierTo());
        Object primaryKeyFrom;
        Object primaryKeyTo;
        if (explicitFrom != null && explicitTo != null) {
            primaryKeyFrom = explicitFrom;
            primaryKeyTo = explicitTo;
        } else {
            // 边按图库里的 From/To 定向：行为对象挂在 To 侧时本端主键落在 To 上，不能一律当 From
            var selfIsFrom = !StringUtils.equals(actionContext.getEntityActionExecuteParam().getOntologyUniqueIdentifier(),
                    link.getOntologyUniqueIdentifierTo());
            // 本端主键同样按对象解析：只传各对象实例主键 map 时也能拿到本端实例
            var selfPrimaryKey = resolveEntityPrimaryKey(actionContext, actionContext.getEntityActionExecuteParam(),
                    actionContext.getEntityActionExecuteParam().getOntologyUniqueIdentifier());
            var counterpartPrimaryKey = actionContext.getLinkEntityPrimaryKey();
            primaryKeyFrom = selfIsFrom ? selfPrimaryKey : counterpartPrimaryKey;
            primaryKeyTo = selfIsFrom ? counterpartPrimaryKey : selfPrimaryKey;
        }

        EntityRelation relation = relationRepository.queryRelationByFromNodeAndToNode(link.getOntologyUniqueIdentifierFrom(),
                primaryKeyFrom, link.getOntologyUniqueIdentifierTo(), primaryKeyTo, linkGroupId);

        if (relation == null) {
            var fromNode = findOrCreateEntityNode(link.getOntologyUniqueIdentifierFrom(), primaryKeyFrom);
            var toNode = findOrCreateEntityNode(link.getOntologyUniqueIdentifierTo(), primaryKeyTo);
            if (fromNode == null || toNode == null) {
                //端点主键为空时无法定位或补建节点，保持跳过
                log.warn("行为函数实例关系边落位跳过：端点主键为空，link={}, fromPk={}, toPk={}",
                        link.getUniqueIdentifier(), primaryKeyFrom, primaryKeyTo);
            } else {
                relationRepository.save(EntityRelation.builder()
                        .ontologyLinkId(link.getUniqueIdentifier())
                        .from(fromNode)
                        .to(toNode)
                        .status(Status.ENABLE)
                        .startTime(startTime)
                        .endTime(endTime)
                        .timeWindows(timeWindows == null ? Lists.<VisibilityWindow>newArrayList() : timeWindows)
                        .createTime(new Date())
                        .updateTime(new Date())
                        .type(link.getType())
                        .name(link.getName())
                        .build());
            }
        } else {
            relationRepository.updateRelation(startTime, endTime, timeWindows, Status.ENABLE, relation.getId());
        }
    }

    /**
     * 新增实体节点：适用于实体增量同步场景
     * <p>
     * 与数据湖 CDC 链路共用 {@link #upsertEntityNode}：节点按归一化主键判存，命中则只刷新展示名，
     * 因此重复调用同一入参只覆盖、不会产生第二份节点。实例关系边不在这里生成，只在关系真实成立或人工激活时落库。
     *
     * @param ontologyUniqueIdentifier 对象id
     * @param entityPropertyMap        实体属性（列名 -> 列值，列名大小写不敏感）
     */
    @Override
    public void completeEntityNode(String ontologyUniqueIdentifier, Map<String, Object> entityPropertyMap) {
        //创建实体节点
        var primaryProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));

        //未绑定主键数据源
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceId())) {
            return;
        }

        var entityPrimaryKeyValue = OntologyInstanceConverter.getColumnValue(
                entityPropertyMap, primaryProperty.getDatasourceColumnName());
        if (entityPrimaryKeyValue == null) {
            log.warn("对象[{}]补全实体节点失败：主键列[{}]在入参中取值为空",
                    ontologyUniqueIdentifier, primaryProperty.getDatasourceColumnName());
            return;
        }

        // 名称键与主键同表时才取名称列，缺省回退主键值，与实例文档 name 同口径
        var displayName = entityPrimaryKeyValue.toString();
        var titleProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsTitleKey, 1));
        if (titleProperty != null
                && StringUtils.equals(titleProperty.getDatasourceId(), primaryProperty.getDatasourceId())) {
            var titleValue = OntologyInstanceConverter.getColumnValue(
                    entityPropertyMap, titleProperty.getDatasourceColumnName());
            if (titleValue != null) {
                displayName = titleValue.toString();
            }
        }

        upsertEntityNode(ontologyUniqueIdentifier, primaryProperty.getDatasourceId(),
                entityPrimaryKeyValue, displayName);
    }

    @Override
    public void deleteEntityNodeAndRelations(String ontologyUniqueIdentifier, Object entityPrimaryKey) {
        var relations = relationRepository.queryAllRelationsByEntities(ontologyUniqueIdentifier, Lists.newArrayList(entityPrimaryKey));
        if (CollectionUtils.isNotEmpty(relations)) {
            // deleteByIds 过滤的是 _id，取 getId()（映射 _key）会一条都删不掉
            relationRepository.deleteByIds(relations.stream().map(EntityRelation::getArangoId).collect(Collectors.toList()));
        }

        var node = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(ontologyUniqueIdentifier, entityPrimaryKey);
        if (node != null) {
            nodeRepository.delete(node);
        }
    }

    /**
     * 取关系端点节点，图库缺节点时按对象主键补建。
     * <p>
     * 行为建边不要求调用方先同步节点；补建时表名取主键属性绑定的数据源，
     * 展示名回查同数据源下的标题列，查不到回退主键字符串。
     */
    private EntityNode findOrCreateEntityNode(String ontologyUniqueIdentifier, Object primaryKeyValue) {
        if (primaryKeyValue == null) {
            return null;
        }
        var normalizedPk = normalizePrimaryKey(primaryKeyValue);
        var exist = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(ontologyUniqueIdentifier, normalizedPk);
        if (exist != null) {
            return exist;
        }
        var primaryProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));
        var tableName = primaryProperty == null ? null : primaryProperty.getDatasourceId();
        var displayName = resolveNodeDisplayName(ontologyUniqueIdentifier, primaryProperty, normalizedPk);
        return nodeRepository.save(buildEntityNode(ontologyUniqueIdentifier, tableName, normalizedPk, displayName));
    }

    /** 补节点展示名：主键表同数据源下的标题列有值就用标题，查询失败或无标题回退主键字符串 */
    private String resolveNodeDisplayName(String ontologyUniqueIdentifier, OntologyProperty primaryProperty, Object primaryKeyValue) {
        var fallback = String.valueOf(primaryKeyValue);
        if (primaryProperty == null
                || StringUtils.isEmpty(primaryProperty.getDatasourceId())
                || StringUtils.isEmpty(primaryProperty.getDatasourceColumnName())) {
            return fallback;
        }
        var titleProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsTitleKey, 1));
        if (titleProperty == null
                || !StringUtils.equals(titleProperty.getDatasourceId(), primaryProperty.getDatasourceId())
                || StringUtils.isEmpty(titleProperty.getDatasourceColumnName())) {
            return fallback;
        }
        try {
            var row = objectMapper.queryDataByPrimaryKey(
                    primaryProperty.getDatasourceSchema(),
                    primaryProperty.getDatasourceId(),
                    Lists.newArrayList(primaryProperty.getDatasourceColumnName(), titleProperty.getDatasourceColumnName()),
                    primaryProperty.getDatasourceColumnName(),
                    primaryKeyValue);
            var titleValue = row == null ? null : row.get(titleProperty.getDatasourceColumnName());
            return titleValue == null ? fallback : titleValue.toString();
        } catch (RuntimeException e) {
            log.warn("补建图节点回查展示名失败，回退主键：ontology={}, pk={}", ontologyUniqueIdentifier, primaryKeyValue, e);
            return fallback;
        }
    }

    @Override
    public boolean upsertEntityNode(String ontologyUniqueIdentifier, String tableName,
                                    Object primaryKeyValue, String displayName) {
        if (primaryKeyValue == null) {
            return false;
        }
        var normalizedPk = normalizePrimaryKey(primaryKeyValue);
        var exist = nodeRepository.findByOntologyUniqIdentifierAndPrimaryKey(ontologyUniqueIdentifier, normalizedPk);
        if (exist != null) {
            if (!StringUtils.equals(exist.getDisplayName(), displayName)) {
                exist.setDisplayName(displayName);
                exist.setUpdateTime(new Date());
                nodeRepository.save(exist);
            }
            return false;
        }
        nodeRepository.save(buildEntityNode(ontologyUniqueIdentifier, tableName, normalizedPk, displayName));
        return true;
    }

    @Override
    public int upsertEntityNodes(String ontologyUniqueIdentifier, String tableName, Map<Object, String> pkToDisplayName) {
        if (MapUtils.isEmpty(pkToDisplayName)) {
            return 0;
        }
        // 一次捞该对象现存节点建索引，避免逐行回读图库
        var existNodes = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .filter(v -> v.getPrimaryKey() != null)
                .collect(Collectors.toMap(v -> pkKey(v.getPrimaryKey()), v -> v, (a, b) -> a));
        var staged = Lists.<EntityNode>newArrayList();
        for (var entry : pkToDisplayName.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            var normalizedPk = normalizePrimaryKey(entry.getKey());
            var existNodeKey = pkKey(normalizedPk);
            var exist = existNodes.get(existNodeKey);
            if (exist != null) {
                if (!StringUtils.equals(exist.getDisplayName(), entry.getValue())) {
                    exist.setDisplayName(entry.getValue());
                    exist.setUpdateTime(new Date());
                    staged.add(exist);
                }
                continue;
            }
            var created = buildEntityNode(ontologyUniqueIdentifier, tableName, normalizedPk, entry.getValue());
            existNodes.put(existNodeKey, created);
            staged.add(created);
        }
        if (CollectionUtils.isNotEmpty(staged)) {
            nodeRepository.batchSave(staged);
        }
        return staged.size();
    }

    @Override
    public int deleteEntityNodeOrphans(String ontologyUniqueIdentifier, Collection<Object> keepPrimaryKeyValues) {
        var keepPkKeys = keepPrimaryKeyValues == null ? Sets.<String>newHashSet()
                : keepPrimaryKeyValues.stream().filter(Objects::nonNull)
                        .map(pk -> pkKey(normalizePrimaryKey(pk))).collect(Collectors.toSet());
        var orphans = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .filter(node -> node.getPrimaryKey() != null && !keepPkKeys.contains(pkKey(node.getPrimaryKey())))
                .collect(Collectors.toList());
        return deleteGraphNodes(orphans);
    }

    /**
     * 删除给定节点及其全部相邻关系边（含已激活的边），先删边再删节点，避免留下悬空边。
     */
    private int deleteGraphNodes(List<EntityNode> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return 0;
        }
        var relations = relationRepository.findByFrom(nodes);
        relations.addAll(relationRepository.findByTo(nodes));
        if (CollectionUtils.isNotEmpty(relations)) {
            relationRepository.deleteByIds(relations.stream().map(EntityRelation::getArangoId).collect(Collectors.toList()));
        }
        nodeRepository.deleteByIds(nodes.stream().map(EntityNode::getArangoId).collect(Collectors.toList()));
        return nodes.size();
    }

    /**
     * 图库主键归一化：数据湖 bigint 经 JDBC 是 Long、经 Debezium JSON 可能是 Integer，
     * 与 {@link #syncNodes} 的转换口径保持一致，否则同一实例会被建出重复节点。
     */
    private Object normalizePrimaryKey(Object primaryKeyValue) {
        if (primaryKeyValue == null) {
            return null;
        }
        if (primaryKeyValue instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                return str;
            }
        }
        if (primaryKeyValue instanceof Integer) {
            return Long.parseLong(primaryKeyValue.toString());
        }
        return primaryKeyValue;
    }

    private EntityNode buildEntityNode(String ontologyUniqueIdentifier, String tableName,
                                       Object primaryKeyValue, String displayName) {
        var now = new Date();
        return EntityNode.builder()
                .ontologyUniqIdentifier(ontologyUniqueIdentifier)
                .primaryKey(primaryKeyValue)
                .tableName(tableName)
                .displayName(displayName)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    @Transactional(value = "datalakeTransactionManager")
    @Override
    public void generateEntities(EntityGenerateParam param) {
        var ontologyMeta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
        PreconditionUtils.checkArgument(ontologyMeta.getCanGenerateEntity(), "该对象不能生成实体对象", ResultCode.NO_PERMISSION, HttpStatus.FORBIDDEN);

        var properties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));
        var pk = properties.stream().filter(v -> v.getIsPrimaryKey().equals(1)).findFirst();
        // 主键不存在或者未绑定主数据源
        if (!pk.isPresent()) {
            return;
        }
        var pkProp = pk.get();
        if (StringUtils.isEmpty(pkProp.getDatasourceId()) || StringUtils.isEmpty(pkProp.getDatasourceColumnName())) {
            return;
        }
        //删除已有实体
        deleteExistEntities(pkProp);
        // 生成新的实体数据
        // 插入实体主属性表
        var allPropMap = properties.stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v));
        var propertyValueMap = param.getPropertyValues().stream().collect(Collectors.toMap(v -> v.getPropertyApiName(), v -> v.getPropertyValue()));
        var pkProperties = properties.stream().filter(p -> StringUtils.equals(p.getDatasourceId(), pkProp.getDatasourceId())
                        && !StringUtils.equals(p.getDatasourceColumnName(), pkProp.getDatasourceColumnName()))
                .collect(Collectors.toList());

        HashMap<String, Object> columnValueMap = pkProperties.stream()
                .collect(HashMap::new,
                        (map, p) -> {
                            String key = p.getDatasourceColumnName();
                            Object value = convert2DataType(
                                    propertyValueMap.get(p.getApiName()),
                                    allPropMap.get(p.getApiName()).getPropertyType()
                            );
                            map.put(key, value); // 允许 null value
                        },
                        Map::putAll
                );

        List<Map<String, Object>> columnValues = new ArrayList<>();
        for (int i = 0; i < param.getCount(); i++) {
            // 使用 new HashMap<>(map) 实现浅复制，避免引用同一对象
            columnValues.add(new HashMap<>(columnValueMap));
        }




        var generateIds = objectMapper.batchInsertObjectReturnKey(pkProp.getDatasourceSchema(), pkProp.getDatasourceId(), columnValues, pkProp.getDatasourceColumnName());
        // 插入关联属性表
        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTable(pkProp.getDatasourceSchema(), pkProp.getDatasourceId());
        tableFieldMappings.stream().forEach(
                tableFieldMapping -> {
                    var targetTableName = tableFieldMapping.getTargetTableName();
                    var props = properties.stream().filter(p -> StringUtils.equals(p.getDatasourceId(), targetTableName)).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(props)) {
                        HashMap<String, Object> valueMap = props.stream()
                                .collect(HashMap::new,
                                        (map, p) -> {
                                            String key = p.getDatasourceColumnName();
                                            Object value = convert2DataType(
                                                    propertyValueMap.get(p.getApiName()),
                                                    allPropMap.get(p.getApiName()).getPropertyType()
                                            );
                                            map.put(key, value); // 允许 null value
                                        },
                                        Map::putAll
                                );

                        List<Map<String, Object>> values = new ArrayList<>();
                        for (int i = 0; i < param.getCount(); i++) {
                            // 使用 new HashMap<>(map) 实现浅复制，避免引用同一对象
                            valueMap.put(tableFieldMapping.getTargetColumnName(), generateIds.get(i));
                            values.add(new HashMap<>(valueMap));
                        }
                        objectMapper.batchInsertObject(pkProp.getDatasourceSchema(), tableFieldMapping.getTargetTableName(), values);
                    }
                }
        );
        // 补全实体节点
        for (int i = 0; i < param.getCount(); i++) {
            var pkValue = generateIds.get(i);
            var entityPropertyMap = columnValues.get(i);
            entityPropertyMap.put(pkProp.getDatasourceColumnName(), pkValue);
            completeEntityNode(param.getOntologyIdentifier(), entityPropertyMap);
        }
    }


    @Override
    public Page<List<EntityPropertyGenericQueryVO>> genericQuery(EntityPropertyGenericQueryParam param) {
        // 1. 校验查询属性是否存在或者是否关联了数据源
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));
        // 查询主键是否存在且关联了数据源
        var pkProp = props.stream().filter(p -> p.getIsPrimaryKey() == 1).findFirst().orElse(null);
        PreconditionUtils.checkArgument(pkProp != null && StringUtils.isNotEmpty(pkProp.getDatasourceId()), "主键属性不存在或未关联数据源", HttpStatus.BAD_REQUEST);
        // 校验 schema 不为空
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(pkProp.getDatasourceSchema()), 
                "对象数据源 schema 为空，请检查数据源配置", HttpStatus.BAD_REQUEST);
        // 同一对象的所有数据源表都在对象所属空间的 schema 下，属性行的 datasource_schema 不可靠（可能为空串或残留 'public'），统一以主表 schema 为准
        var mainSchema = pkProp.getDatasourceSchema();
        // 校验返回属性是否存在或者是否关联了数据源
        var propMap = props.stream().collect(Collectors.toMap(OntologyProperty::getApiName, v -> v));
        param.getSelectProperties().forEach(p -> {
            var findProp = propMap.get(p.getPropertyApiName());
            PreconditionUtils.checkArgument(findProp != null && StringUtils.isNotEmpty(findProp.getDatasourceId()), "属性" + p.getPropertyApiName() + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);
        });
        //主属性数据源表
        var mainDs = pkProp.getDatasourceId();
        // 2. 收集所有表的关联映射
        Map<String, TableFieldMapping> tableMappingMap = new HashMap<>();
        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTable(pkProp.getDatasourceSchema(), mainDs);
        tableFieldMappings.forEach(mapping -> tableMappingMap.put(mapping.getTargetTableName(), mapping));
        // 3. 构建 SELECT 部分
        var selectBuilder = new StringBuilder();
        List<String> selectColumnList = Lists.newArrayList();
        Map<String, OntologySelectPropertyParam> selectParamMap = Maps.newLinkedHashMap();
        boolean hasAggregation = false;
        for (OntologySelectPropertyParam selectProp : param.getSelectProperties()) {
            if (selectBuilder.length() > 0) {
                selectBuilder.append(",");
            }
            var prop = propMap.get(selectProp.getPropertyApiName());
            var aliasKey = StringUtils.isNotEmpty(selectProp.getAlias()) ? selectProp.getAlias() : selectProp.getPropertyApiName();
            selectParamMap.put(aliasKey, selectProp);
            var columnRef = wrapColumnRef(mainSchema, prop.getDatasourceId(), prop.getDatasourceColumnName());

            if (selectProp.getAggFunc() != null) {
                hasAggregation = true;
                switch (selectProp.getAggFunc()) {
                    case COUNT:
                    case MAX:
                    case MIN:
                    case DISTINCT:
                        selectBuilder.append(selectProp.getAggFunc().getValue()).append("(").append(columnRef).append(")");
                        break;
                    case SUM:
                    case AVG:
                        selectBuilder.append(selectProp.getAggFunc().getValue()).append("(")
                                .append(toNumericExpr(columnRef, prop.getPropertyType())).append(")");
                        break;
                    default:
                        selectBuilder.append(columnRef);
                }
            } else {
                selectBuilder.append(columnRef);
            }
            var selectColumn = StringUtils.isNotEmpty(selectProp.getAlias()) ? selectProp.getAlias() : selectProp.getPropertyApiName();
            selectBuilder.append(" AS ").append(wrapWithDoubleQuotes(selectColumn));
            selectColumnList.add(selectColumn);
        }
        // 4. 构建 FROM 和 LEFT JOIN
        var fromBuilder = new StringBuilder();
        fromBuilder.append(wrapTableRef(mainSchema, mainDs));
        var joinedTables = Sets.newHashSet();
        joinedTables.add(mainDs);
        // 收集所有涉及的属性API名称
        Set<String> allPropApiNames = new LinkedHashSet<>();
        // select属性
        for (var selectProp : param.getSelectProperties()) {
            allPropApiNames.add(selectProp.getPropertyApiName());
        }
        // 过滤条件属性
        allPropApiNames.addAll(collectFilterPropertyApiNames(param.getFilters()));
        // 分组属性
        if (CollectionUtils.isNotEmpty(param.getGroupBy())) {
            allPropApiNames.addAll(param.getGroupBy());
        }
        // 排序属性
        if (CollectionUtils.isNotEmpty(param.getOrderBy())) {
            for (OrderByParam orderBy : param.getOrderBy()) {
                allPropApiNames.add(orderBy.getPropertyApiName());
            }
        }
        // 统一添加LEFT JOIN
        for (var apiName : allPropApiNames) {
            var prop = propMap.get(apiName);
            if (prop == null || StringUtils.isEmpty(prop.getDatasourceId())) {
                continue;
            }
            var dsId = prop.getDatasourceId();
            if (joinedTables.contains(dsId)) {
                continue;
            }
            var mapping = tableMappingMap.get(dsId);
            PreconditionUtils.checkArgument(mapping != null, "未找到数据源表 " + dsId + " 与主表 " + mainDs + " 的关联关系", HttpStatus.BAD_REQUEST);
            fromBuilder.append(" LEFT JOIN ").append(wrapTableRef(mainSchema, dsId))
                    .append(" ON ").append(wrapColumnRef(mainSchema, mainDs, mapping.getSourceColumnName())).append(" = ")
                    .append(wrapColumnRef(mainSchema, dsId, mapping.getTargetColumnName()));
            joinedTables.add(dsId);
        }
        // 5. 构建 WHERE 部分
        // 按实例查询：主键等值条件并进过滤树，与请求/算子原有条件是 AND，不替换也不改变原条件的括号语义
        if (param.getEntityPrimaryKey() != null) {
            param.setFilters(andPrimaryKeyFilter(param.getFilters(), pkProp.getApiName(), param.getEntityPrimaryKey()));
        }
        // 先校验过滤值类型（属性存在性 + 数值/布尔/日期可解析），拦截空串等脏值，
        // 否则数值型空串会在 formatSqlValue 拼出 "col = " 残缺 SQL，抛数据库层语法错误
        if (param.getFilters() != null) {
            validateFilterGroup(param.getFilters(), propMap);
        }
        var whereBuilder = new StringBuilder();
        if (param.getFilters() != null && CollectionUtils.isNotEmpty(param.getFilters().getChildren())) {
            var whereClause = buildWhereClause(param.getFilters(), propMap, mainSchema);
            if (StringUtils.isNotEmpty(whereClause)) {
                whereBuilder.append(" WHERE ").append(whereClause);
            }
        }
        // 6. 构建 GROUP BY
        var groupByBuilder = new StringBuilder();
        if (CollectionUtils.isNotEmpty(param.getGroupBy())) {
            groupByBuilder.append(" GROUP BY ");
            var first = true;
            for (var groupByProp : param.getGroupBy()) {
                if (!first) {
                    groupByBuilder.append(",");
                }
                var prop = propMap.get(groupByProp);
                PreconditionUtils.checkArgument(prop != null && StringUtils.isNotEmpty(prop.getDatasourceId()),
                        "分组属性" + groupByProp + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);
                groupByBuilder.append(wrapColumnRef(mainSchema, prop.getDatasourceId(), prop.getDatasourceColumnName()));
                first = false;
            }
        }
        // 7. 构建 ORDER BY
        var orderByBuilder = new StringBuilder();
        if (CollectionUtils.isNotEmpty(param.getOrderBy())) {
            orderByBuilder.append(" ORDER BY ");
            var first = true;
            for (var orderBy : param.getOrderBy()) {
                if (!first) {
                    orderByBuilder.append(",");
                }
                var prop = propMap.get(orderBy.getPropertyApiName());
                PreconditionUtils.checkArgument(prop != null && StringUtils.isNotEmpty(prop.getDatasourceId()),
                        "排序属性" + orderBy.getPropertyApiName() + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);
                orderByBuilder.append(wrapColumnRef(mainSchema, prop.getDatasourceId(), prop.getDatasourceColumnName())).append(" ").append(orderBy.getSort().name());
                first = false;
            }
        }
        // 8. 组装SQL并执行
        var pageSize = param.getPageSize();
        var offset = (param.getPageNum() - 1) * pageSize;
        // base SQL
        var baseSql = "SELECT " + selectBuilder + " FROM " + fromBuilder + whereBuilder + groupByBuilder + orderByBuilder;
        
        long total;
        String dataSql;
        if (hasAggregation) {
            // 聚合查询不需要分页，直接返回结果
            total = 1;
            dataSql = baseSql;
        } else {
            // 计数SQL
            var countSql = "SELECT COUNT(*) FROM (" + baseSql + ") AS t";
            total = objectMapper.queryCountBySql(countSql);
            if (total == 0) {
                return new Page<>(param.getPageNum(), pageSize, 0);
            }
            // 数据SQL
            dataSql = baseSql + " LIMIT " + pageSize + " OFFSET " + offset;
        }
        List<Map<String, Object>> resultMaps;
        try {
            resultMaps = objectMapper.queryBySql(dataSql);
        } catch (DataAccessException e) {
            log.error("通用查询执行失败, sql: {}", dataSql, e);
            if (StringUtils.defaultString(e.getMostSpecificCause().getMessage())
                    .contains("invalid input syntax for type numeric")) {
                throw new BusinessException("聚合列存在非数字数据，无法转换为数值类型", HttpStatus.BAD_REQUEST);
            }
            throw e;
        }
        // 9. 转换结果
        List<List<EntityPropertyGenericQueryVO>> resultVOs = Lists.newArrayList();
        for (var row : resultMaps) {
            List<EntityPropertyGenericQueryVO> voList = new ArrayList<>();
            for (var column : selectColumnList) {
                var selectProp = selectParamMap.get(column);
                if (selectProp == null) {
                    continue;
                }
                var prop = propMap.get(selectProp.getPropertyApiName());
                // row 为 null 时（聚合无匹配行），value 为 null
                Object value = (row != null) ? row.get(column) : null;
                voList.add(EntityPropertyGenericQueryVO.builder()
                        .propertyApiName(selectProp.getPropertyApiName())
                        .propertyDisplayName(prop != null ? prop.getDisplayName() : selectProp.getPropertyApiName())
                        .aggFunc(selectProp.getAggFunc())
                        .alias(selectProp.getAlias())
                        .value(value)
                        .build());
            }
            resultVOs.add(voList);
        }
        Page<List<EntityPropertyGenericQueryVO>> page = new Page<>(param.getPageNum(), pageSize, total);
        page.setRecords(resultVOs);
        return page;
    }


    /** 统一聚合内层分支投影出的那一列的列名：各对象的取值都叫这个名，外层只对它做一次聚合函数 */
    private static final String UNION_VALUE_COLUMN = "union_value";

    /** 统一聚合外层子查询的表别名 */
    private static final String UNION_SOURCE_ALIAS = "union_src";

    /** 跨对象合并查询外层子查询的表别名：分页与计数包在 UNION ALL 之外 */
    private static final String UNION_QUERY_ALIAS = "union_query_src";

    @Override
    public Page<List<EntityPropertyGenericQueryVO>> genericQueryUnion(EntityUnionQueryParam param) {
        PreconditionUtils.checkNotNull(param, "合并查询参数不能为空", HttpStatus.BAD_REQUEST);
        var identifiers = param.getOntologyIdentifiers() == null ? List.<String>of()
                : param.getOntologyIdentifiers().stream().filter(StringUtils::isNotEmpty)
                .distinct().collect(Collectors.toList());
        PreconditionUtils.checkArgument(!identifiers.isEmpty(), "合并查询至少要有一个对象", HttpStatus.BAD_REQUEST);
        // 字段按 apiName 共用，重复的只查一次
        var selectParams = new LinkedHashMap<String, OntologySelectPropertyParam>();
        if (param.getSelectProperties() != null) {
            for (var selectProp : param.getSelectProperties()) {
                if (selectProp != null && StringUtils.isNotEmpty(selectProp.getPropertyApiName())) {
                    selectParams.putIfAbsent(selectProp.getPropertyApiName(), selectProp);
                }
            }
        }
        PreconditionUtils.checkArgument(!selectParams.isEmpty(), "合并查询至少要选择一个字段", HttpStatus.BAD_REQUEST);
        var selectProperties = new ArrayList<>(selectParams.values());
        var pageNum = param.getPageNum() == null || param.getPageNum() < 1 ? 1 : param.getPageNum();
        var pageSize = param.getPageSize() == null || param.getPageSize() < 1 ? 10 : param.getPageSize();

        var branches = new ArrayList<UnionQueryBranch>();
        for (var identifier : identifiers) {
            branches.add(buildUnionQueryBranch(identifier,
                    param.getEntityPrimaryKeys() == null ? null : param.getEntityPrimaryKeys().get(identifier),
                    selectProperties, param.getFilters()));
        }
        var unionSql = branches.stream().map(UnionQueryBranch::sql).collect(Collectors.joining(" UNION ALL "));
        var unionAlias = wrapWithDoubleQuotes(UNION_QUERY_ALIAS);
        var total = objectMapper.queryCountBySql("SELECT COUNT(*) FROM (" + unionSql + ") AS " + unionAlias);
        if (total == 0) {
            return new Page<>(pageNum, pageSize, 0);
        }
        var offset = (long) (pageNum - 1) * pageSize;
        var dataSql = "SELECT * FROM (" + unionSql + ") AS " + unionAlias
                + " LIMIT " + pageSize + " OFFSET " + offset;
        List<Map<String, Object>> resultMaps;
        try {
            resultMaps = objectMapper.queryBySql(dataSql);
        } catch (DataAccessException e) {
            log.error("跨对象合并查询执行失败, sql: {}", dataSql, e);
            var cause = StringUtils.defaultString(e.getMostSpecificCause().getMessage());
            if (cause.contains("cannot be matched")) {
                throw new BusinessException("各对象上同名字段关联的数据源列类型不一致，合不到一列里，请检查这些字段的数据类型",
                        HttpStatus.BAD_REQUEST);
            }
            throw e;
        }
        // 字段展示名按第一个对象取：合批之后的值认不出来自哪个对象，展示名只是这一列的表头
        var firstPropMap = branches.get(0).propMap();
        var resultVOs = new ArrayList<List<EntityPropertyGenericQueryVO>>(resultMaps.size());
        for (var row : resultMaps) {
            var voList = new ArrayList<EntityPropertyGenericQueryVO>(selectProperties.size());
            for (var selectProp : selectProperties) {
                var apiName = selectProp.getPropertyApiName();
                var prop = firstPropMap.get(apiName);
                voList.add(EntityPropertyGenericQueryVO.builder()
                        .propertyApiName(apiName)
                        .propertyDisplayName(prop != null ? prop.getDisplayName() : apiName)
                        .alias(selectProp.getAlias())
                        .value(row == null ? null : row.get(apiName))
                        .build());
            }
            resultVOs.add(voList);
        }
        var page = new Page<List<EntityPropertyGenericQueryVO>>(pageNum, pageSize, total);
        page.setRecords(resultVOs);
        return page;
    }

    /**
     * 合并查询的一个对象分支：按该对象自己的属性表把选中字段的列查出来，列名统一用 apiName。
     * <p>表的关联、实例主键与过滤条件的处理与 {@link #genericQuery} 同一套：条件里引用到子表字段时
     * 一样要 LEFT JOIN 过来，命中几行就贡献几行。</p>
     */
    private UnionQueryBranch buildUnionQueryBranch(String ontologyIdentifier, Object entityPrimaryKey,
                                                   List<OntologySelectPropertyParam> selectProperties,
                                                   FilterGroupParam filters) {
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(ontologyIdentifier), "合并查询的对象不能为空", HttpStatus.BAD_REQUEST);
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        var propMap = props.stream()
                .collect(Collectors.toMap(OntologyProperty::getApiName, v -> v, (existing, ignored) -> existing));
        var pkProp = props.stream()
                .filter(p -> p.getIsPrimaryKey() != null && p.getIsPrimaryKey() == 1).findFirst().orElse(null);
        // 报错文案要点对象名，判定通过时不去查：checkArgument 的消息参数是先求值的，放这里每个对象都要白查一遍库
        if (pkProp == null || StringUtils.isEmpty(pkProp.getDatasourceId())) {
            throw new BusinessException("对象[" + objectDisplayName(ontologyIdentifier)
                    + "]的主键属性不存在或未关联数据源，合并查询跑不出来", HttpStatus.BAD_REQUEST);
        }
        if (StringUtils.isEmpty(pkProp.getDatasourceSchema())) {
            throw new BusinessException("对象[" + objectDisplayName(ontologyIdentifier)
                    + "]的数据源 schema 为空，请检查数据源配置", HttpStatus.BAD_REQUEST);
        }
        var mainSchema = pkProp.getDatasourceSchema();
        var mainDs = pkProp.getDatasourceId();
        var selectBuilder = new StringBuilder();
        var allPropApiNames = new LinkedHashSet<String>();
        for (var selectProp : selectProperties) {
            var apiName = selectProp.getPropertyApiName();
            var prop = propMap.get(apiName);
            if (prop == null || StringUtils.isEmpty(prop.getDatasourceId())) {
                throw new BusinessException("对象[" + objectDisplayName(ontologyIdentifier) + "]的属性" + apiName
                        + "不存在或未关联数据源，合并查询要求每个对象上都有这些字段", HttpStatus.BAD_REQUEST);
            }
            if (selectBuilder.length() > 0) {
                selectBuilder.append(",");
            }
            selectBuilder.append(wrapColumnRef(mainSchema, prop.getDatasourceId(), prop.getDatasourceColumnName()))
                    .append(" AS ").append(wrapWithDoubleQuotes(apiName));
            allPropApiNames.add(apiName);
        }
        allPropApiNames.addAll(collectFilterPropertyApiNames(filters));
        var fromBuilder = new StringBuilder(wrapTableRef(mainSchema, mainDs));
        var tableMappingMap = new HashMap<String, TableFieldMapping>();
        tableFieldMappingMapper.selectBySourceTable(mainSchema, mainDs)
                .forEach(mapping -> tableMappingMap.put(mapping.getTargetTableName(), mapping));
        var joinedTables = Sets.newHashSet(mainDs);
        for (var apiName : allPropApiNames) {
            var joinProp = propMap.get(apiName);
            if (joinProp == null || StringUtils.isEmpty(joinProp.getDatasourceId())
                    || !joinedTables.add(joinProp.getDatasourceId())) {
                continue;
            }
            var mapping = tableMappingMap.get(joinProp.getDatasourceId());
            PreconditionUtils.checkArgument(mapping != null,
                    "未找到数据源表 " + joinProp.getDatasourceId() + " 与主表 " + mainDs + " 的关联关系", HttpStatus.BAD_REQUEST);
            fromBuilder.append(" LEFT JOIN ").append(wrapTableRef(mainSchema, joinProp.getDatasourceId()))
                    .append(" ON ").append(wrapColumnRef(mainSchema, mainDs, mapping.getSourceColumnName())).append(" = ")
                    .append(wrapColumnRef(mainSchema, joinProp.getDatasourceId(), mapping.getTargetColumnName()));
        }
        var branchFilters = filters;
        if (entityPrimaryKey != null) {
            branchFilters = andPrimaryKeyFilter(branchFilters, pkProp.getApiName(), entityPrimaryKey);
        }
        if (branchFilters != null) {
            validateFilterGroup(branchFilters, propMap);
        }
        var whereBuilder = new StringBuilder();
        if (branchFilters != null && CollectionUtils.isNotEmpty(branchFilters.getChildren())) {
            var whereClause = buildWhereClause(branchFilters, propMap, mainSchema);
            if (StringUtils.isNotEmpty(whereClause)) {
                whereBuilder.append(" WHERE ").append(whereClause);
            }
        }
        return new UnionQueryBranch("SELECT " + selectBuilder + " FROM " + fromBuilder + whereBuilder, propMap);
    }

    /** 合并查询的一个对象分支：拼好的查询 SQL + 该对象的属性表 */
    private record UnionQueryBranch(String sql, Map<String, OntologyProperty> propMap) {
    }

    @Override
    public Page<List<EntityPropertyGenericQueryVO>> genericAggregateUnion(EntityUnionAggregateParam param) {
        PreconditionUtils.checkNotNull(param, "统一聚合查询参数不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkNotNull(param.getAggFunc(), "统一聚合要指定聚合函数", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(param.getPropertyApiName()),
                "统一聚合要指定聚合属性", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(param.getSources()),
                "统一聚合至少要有一个来源对象", HttpStatus.BAD_REQUEST);
        var branches = param.getSources().stream()
                .map(source -> buildUnionAggregateBranch(source, param.getPropertyApiName(), param.getAggFunc()))
                .collect(Collectors.toList());
        // 属性展示名按第一个来源对象给：合批之后的值认不出各自来自哪个对象，展示名只是这一列的表头
        var propertyDisplayName = branches.get(0).property().getDisplayName();
        checkUnionBranchValueTypes(param.getAggFunc(), propertyDisplayName, branches);
        var alias = param.getAggFunc().getValue().toLowerCase() + "_" + param.getPropertyApiName();
        var valueColumn = wrapTableRef(UNION_SOURCE_ALIAS, UNION_VALUE_COLUMN);
        // DISTINCT 没有可下推到外层的聚合函数：单对象时 DISTINCT(col) 回的就是去重后的那批值，
        // 这里对合起来的整批值去重，口径保持一致（回值，不回条数）
        var selectBuilder = new StringBuilder("SELECT ");
        if (param.getAggFunc() == AggFuncEnum.DISTINCT) {
            selectBuilder.append("DISTINCT ").append(valueColumn);
        } else {
            selectBuilder.append(param.getAggFunc().getValue()).append("(").append(valueColumn).append(")");
        }
        var dataSql = selectBuilder.append(" AS ").append(wrapWithDoubleQuotes(alias))
                .append(" FROM (").append(String.join(" UNION ALL ",
                        branches.stream().map(UnionAggregateBranch::sql).collect(Collectors.toList()))).append(") ")
                .append(wrapWithDoubleQuotes(UNION_SOURCE_ALIAS)).toString();
        List<Map<String, Object>> resultMaps;
        try {
            resultMaps = objectMapper.queryBySql(dataSql);
        } catch (DataAccessException e) {
            log.error("统一聚合查询执行失败, sql: {}", dataSql, e);
            var cause = StringUtils.defaultString(e.getMostSpecificCause().getMessage());
            if (cause.contains("invalid input syntax for type numeric")) {
                throw new BusinessException("聚合列存在非数字数据，无法转换为数值类型", HttpStatus.BAD_REQUEST);
            }
            if (cause.contains("cannot be matched")) {
                throw new BusinessException("各来源对象的属性[" + propertyDisplayName
                        + "]关联的数据源字段类型不一致，合不成一批，无法统一聚合，请检查这些对象上该属性的字段类型",
                        HttpStatus.BAD_REQUEST);
            }
            throw e;
        }
        var resultVOs = new ArrayList<List<EntityPropertyGenericQueryVO>>();
        for (var row : resultMaps) {
            resultVOs.add(List.of(EntityPropertyGenericQueryVO.builder()
                    .propertyApiName(param.getPropertyApiName())
                    .propertyDisplayName(propertyDisplayName)
                    .aggFunc(param.getAggFunc())
                    .alias(alias)
                    .value(row == null ? null : row.get(alias))
                    .build()));
        }
        var page = new Page<List<EntityPropertyGenericQueryVO>>(1, Math.max(resultVOs.size(), 1), resultVOs.size());
        page.setRecords(resultVOs);
        return page;
    }

    /**
     * 统一聚合的一个对象分支：把该对象上被聚合的属性按 {@link #unionBranchExpr} 的取值形状投影成一列，聚合留给外层做。
     * <p>表的关联、实例主键与过滤条件的处理与 {@link #genericQuery} 同一套：条件里引用到子表字段时
     * 一样要 LEFT JOIN 过来，命中几行就贡献几个值。</p>
     */
    private UnionAggregateBranch buildUnionAggregateBranch(EntityUnionAggregateParam.Source source,
                                                           String propertyApiName, AggFuncEnum aggFunc) {
        PreconditionUtils.checkArgument(source != null && StringUtils.isNotEmpty(source.getOntologyIdentifier()),
                "统一聚合的来源对象不能为空", HttpStatus.BAD_REQUEST);
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, source.getOntologyIdentifier()));
        var propMap = props.stream()
                .collect(Collectors.toMap(OntologyProperty::getApiName, v -> v, (existing, ignored) -> existing));
        var pkProp = props.stream()
                .filter(p -> p.getIsPrimaryKey() != null && p.getIsPrimaryKey() == 1).findFirst().orElse(null);
        // 报错文案要点对象名，判定通过时不去查：checkArgument 的消息参数是先求值的，放这里每个对象都要白查一遍库
        if (pkProp == null || StringUtils.isEmpty(pkProp.getDatasourceId())) {
            throw new BusinessException("对象[" + objectDisplayName(source.getOntologyIdentifier())
                    + "]的主键属性不存在或未关联数据源，统一聚合跑不出来", HttpStatus.BAD_REQUEST);
        }
        if (StringUtils.isEmpty(pkProp.getDatasourceSchema())) {
            throw new BusinessException("对象[" + objectDisplayName(source.getOntologyIdentifier())
                    + "]的数据源 schema 为空，请检查数据源配置", HttpStatus.BAD_REQUEST);
        }
        var prop = propMap.get(propertyApiName);
        if (prop == null || StringUtils.isEmpty(prop.getDatasourceId())) {
            throw new BusinessException("对象[" + objectDisplayName(source.getOntologyIdentifier()) + "]的属性"
                    + propertyApiName + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);
        }
        var mainSchema = pkProp.getDatasourceSchema();
        var mainDs = pkProp.getDatasourceId();
        var columnRef = wrapColumnRef(mainSchema, prop.getDatasourceId(), prop.getDatasourceColumnName());
        var selectBuilder = new StringBuilder("SELECT ")
                .append(unionBranchExpr(aggFunc, columnRef, prop.getPropertyType()))
                .append(" AS ").append(wrapWithDoubleQuotes(UNION_VALUE_COLUMN));
        var fromBuilder = new StringBuilder(wrapTableRef(mainSchema, mainDs));
        var tableMappingMap = new HashMap<String, TableFieldMapping>();
        tableFieldMappingMapper.selectBySourceTable(mainSchema, mainDs)
                .forEach(mapping -> tableMappingMap.put(mapping.getTargetTableName(), mapping));
        var joinedTables = Sets.newHashSet(mainDs);
        for (var apiName : collectUnionBranchPropertyApiNames(propertyApiName, source.getFilters())) {
            var joinProp = propMap.get(apiName);
            if (joinProp == null || StringUtils.isEmpty(joinProp.getDatasourceId())
                    || !joinedTables.add(joinProp.getDatasourceId())) {
                continue;
            }
            var mapping = tableMappingMap.get(joinProp.getDatasourceId());
            PreconditionUtils.checkArgument(mapping != null,
                    "未找到数据源表 " + joinProp.getDatasourceId() + " 与主表 " + mainDs + " 的关联关系", HttpStatus.BAD_REQUEST);
            fromBuilder.append(" LEFT JOIN ").append(wrapTableRef(mainSchema, joinProp.getDatasourceId()))
                    .append(" ON ").append(wrapColumnRef(mainSchema, mainDs, mapping.getSourceColumnName())).append(" = ")
                    .append(wrapColumnRef(mainSchema, joinProp.getDatasourceId(), mapping.getTargetColumnName()));
        }
        var filters = source.getFilters();
        if (source.getEntityPrimaryKey() != null) {
            filters = andPrimaryKeyFilter(filters, pkProp.getApiName(), source.getEntityPrimaryKey());
        }
        if (filters != null) {
            validateFilterGroup(filters, propMap);
        }
        var whereBuilder = new StringBuilder();
        if (filters != null && CollectionUtils.isNotEmpty(filters.getChildren())) {
            var whereClause = buildWhereClause(filters, propMap, mainSchema);
            if (StringUtils.isNotEmpty(whereClause)) {
                whereBuilder.append(" WHERE ").append(whereClause);
            }
        }
        return new UnionAggregateBranch(selectBuilder.append(" FROM ").append(fromBuilder).append(whereBuilder).toString(), prop);
    }

    /** 统一聚合分支要关联的表：被聚合的属性 + 条件里引用的属性 */
    private Set<String> collectUnionBranchPropertyApiNames(String propertyApiName, FilterGroupParam filters) {
        var apiNames = new LinkedHashSet<String>();
        apiNames.add(propertyApiName);
        apiNames.addAll(collectFilterPropertyApiNames(filters));
        return apiNames;
    }

    /** 统一聚合的一个对象分支：拼好的取值 SQL + 该对象上被取的那个属性 */
    private record UnionAggregateBranch(String sql, OntologyProperty property) {
    }

    /**
     * 统一聚合里一个分支取值的写法：各分支合批前先把形状对齐，外层才能只调一次聚合函数。
     * <ul>
     *   <li>SUM/AVG：转成 numeric。各对象声明的属性类型可以不同（字符串记的数值），不先统一会被数据库拒掉。</li>
     *   <li>COUNT：只数非空，转成 text 保证各分支合得到一起，空不空的判定不受影响。</li>
     *   <li>MAX/MIN/DISTINCT：要的是属性原值和原顺序，原样取。</li>
     * </ul>
     */
    private static String unionBranchExpr(AggFuncEnum aggFunc, String columnRef, OntologyDataTypeEnum propType) {
        if (aggFunc == AggFuncEnum.SUM || aggFunc == AggFuncEnum.AVG) {
            return toNumericExpr(columnRef, propType);
        }
        if (aggFunc == AggFuncEnum.COUNT) {
            return "(" + columnRef + ")::text";
        }
        return columnRef;
    }

    /**
     * MAX/MIN/DISTINCT 跨对象合批的前置检查：这些函数拿的是属性原值，各对象声明的类型不一致时
     * 数据库会自己找一个公共类型（数值和文本混批会统一成文本），MAX 就按字典序比出「9 大于 10」这种看着像、其实是错的值。
     * <p>与其静默算错，不如在跑之前点名要求一致；SUM/AVG/COUNT 已各自把取值统一成 numeric/text，不收这条限制。</p>
     */
    private void checkUnionBranchValueTypes(AggFuncEnum aggFunc, String propertyDisplayName,
                                            List<UnionAggregateBranch> branches) {
        if (aggFunc != AggFuncEnum.MAX && aggFunc != AggFuncEnum.MIN && aggFunc != AggFuncEnum.DISTINCT) {
            return;
        }
        var types = branches.stream()
                .map(branch -> branch.property().getPropertyType())
                .filter(Objects::nonNull)
                .map(OntologyDataTypeEnum::getValue)
                .distinct()
                .collect(Collectors.toList());
        if (types.size() <= 1) {
            return;
        }
        throw new BusinessException("各来源对象的属性[" + propertyDisplayName + "]声明的数据类型不一致（"
                + String.join("、", types) + "），" + aggFunc.getValue()
                + " 取的是属性原值，跨类型合批会按文本比出错值；请把这些对象上该属性的数据类型改成一致后再执行",
                HttpStatus.BAD_REQUEST);
    }

    /** 报错文案里的对象名：调用侧只有对象id，露 uuid 定位不到是哪个对象 */
    private String objectDisplayName(String ontologyIdentifier) {
        var meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, ontologyIdentifier), false);
        return meta == null ? ontologyIdentifier : meta.getDisplayName();
    }


    private static String wrapWithDoubleQuotes(String str) {
        return StringUtils.wrap(str, "\"");
    }

    private static String wrapTableRef(String schema, String tableName) {
        return wrapWithDoubleQuotes(schema) + "." + wrapWithDoubleQuotes(tableName);
    }

    private static String wrapColumnRef(String schema, String tableName, String columnName) {
        return wrapTableRef(schema, tableName) + "." + wrapWithDoubleQuotes(columnName);
    }

    /**
     * SUM/AVG 可直接下推到数据库的数值属性类型。
     */
    private static final Set<OntologyDataTypeEnum> NUMERIC_PROPERTY_TYPES = EnumSet.of(
            OntologyDataTypeEnum.Byte, OntologyDataTypeEnum.Short, OntologyDataTypeEnum.Int,
            OntologyDataTypeEnum.Long, OntologyDataTypeEnum.Float, OntologyDataTypeEnum.Double,
            OntologyDataTypeEnum.Decimal);

    private static String toNumericExpr(String columnRef, OntologyDataTypeEnum propType) {
        if (propType != null && NUMERIC_PROPERTY_TYPES.contains(propType)) {
            return columnRef;
        }
        return "(NULLIF((" + columnRef + ")::text, '')::numeric)";
    }

    private String buildWhereClause(FilterGroupParam group, Map<String, OntologyProperty> propMap, String schema) {
        if (CollectionUtils.isEmpty(group.getChildren())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String logic = group.getLogic() != null ? group.getLogic().name() : "AND";
        boolean firstGroup = true;
        for (FilterNodeParam node : group.getChildren()) {
            if (!firstGroup) {
                sb.append(" ").append(logic).append(" ");
            }
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                String filterClause = buildFilterClause(node.getFilter(), propMap, schema);
                if (StringUtils.isNotEmpty(filterClause)) {
                    sb.append(filterClause);
                }
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                String subGroup = buildWhereClause(node.getGroup(), propMap, schema);
                if (StringUtils.isNotEmpty(subGroup)) {
                    sb.append("(").append(subGroup).append(")");
                }
            }
            firstGroup = false;
        }
        return sb.toString();
    }

    /**
     * 把「主键 = 实例」并进过滤树：原有过滤整组括起来与新条件 AND，
     * 原条件是 OR 时也不会因为直接平铺而被主键条件吞掉。
     */
    private FilterGroupParam andPrimaryKeyFilter(FilterGroupParam filters, String pkApiName, Object entityPrimaryKey) {
        var pkNode = FilterNodeParam.builder()
                .type(FilterNodeTypeEnum.FILTER)
                .filter(PropertyFilterParam.builder()
                        .propertyApiName(pkApiName)
                        .op(QueryOpEnum.EQ)
                        .value(entityPrimaryKey)
                        .build())
                .build();
        List<FilterNodeParam> children = new ArrayList<>();
        if (filters != null && CollectionUtils.isNotEmpty(filters.getChildren())) {
            children.add(FilterNodeParam.builder().type(FilterNodeTypeEnum.GROUP).group(filters).build());
        }
        children.add(pkNode);
        return FilterGroupParam.builder().logic(LogicEnum.AND).children(children).build();
    }

    private Set<String> collectFilterPropertyApiNames(FilterGroupParam group) {
        Set<String> result = Sets.newHashSet();
        if (group == null || CollectionUtils.isEmpty(group.getChildren())) {
            return result;
        }
        for (FilterNodeParam node : group.getChildren()) {
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                result.add(node.getFilter().getPropertyApiName());
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                result.addAll(collectFilterPropertyApiNames(node.getGroup()));
            }
        }
        return result;
    }

    private String buildFilterClause(PropertyFilterParam filter, Map<String, OntologyProperty> propMap, String schema) {
        OntologyProperty prop = propMap.get(filter.getPropertyApiName());
        PreconditionUtils.checkArgument(prop != null && StringUtils.isNotEmpty(prop.getDatasourceId()),
                "过滤属性" + filter.getPropertyApiName() + "不存在或未关联数据源", HttpStatus.BAD_REQUEST);

        String columnRef = wrapColumnRef(schema, prop.getDatasourceId(), prop.getDatasourceColumnName());
        QueryOpEnum op = filter.getOp() != null ? filter.getOp() : QueryOpEnum.EQ;
        
        // 优先使用 filter 声明的 dataType，否则使用属性定义的类型
        OntologyDataTypeEnum effectiveType = getEffectiveType(filter.getDataType(), prop.getPropertyType());

        switch (op) {
            case EQ:
                return columnRef + " = " + formatSqlValue(filter.getValue(), effectiveType);
            case NE:
                return columnRef + " != " + formatSqlValue(filter.getValue(), effectiveType);
            case LIKE:
                return columnRef + " LIKE " + formatSqlValue("%" + filter.getValue() + "%", OntologyDataTypeEnum.String);
            case LIKE_LEFT:
                return columnRef + " LIKE " + formatSqlValue("%" + filter.getValue(), OntologyDataTypeEnum.String);
            case LIKE_RIGHT:
                return columnRef + " LIKE " + formatSqlValue(filter.getValue() + "%", OntologyDataTypeEnum.String);
            case IN:
                if (CollectionUtils.isEmpty(filter.getValues())) {
                    return "1=0";
                }
                StringBuilder inSb = new StringBuilder(columnRef).append(" IN (");
                for (int i = 0; i < filter.getValues().size(); i++) {
                    if (i > 0) {
                        inSb.append(",");
                    }
                    inSb.append(formatSqlValue(filter.getValues().get(i), effectiveType));
                }
                inSb.append(")");
                return inSb.toString();
            case NOT_IN:
                if (CollectionUtils.isEmpty(filter.getValues())) {
                    return "1=1";
                }
                StringBuilder notInSb = new StringBuilder(columnRef).append(" NOT IN (");
                for (int i = 0; i < filter.getValues().size(); i++) {
                    if (i > 0) {
                        notInSb.append(",");
                    }
                    notInSb.append(formatSqlValue(filter.getValues().get(i), effectiveType));
                }
                notInSb.append(")");
                return notInSb.toString();
            case BETWEEN:
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(filter.getValues()) && filter.getValues().size() >= 2,
                        "BETWEEN 需要提供两个值", HttpStatus.BAD_REQUEST);
                return columnRef + " BETWEEN " + formatSqlValue(filter.getValues().get(0), effectiveType)
                        + " AND " + formatSqlValue(filter.getValues().get(1), effectiveType);
            case NOT_BETWEEN:
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(filter.getValues()) && filter.getValues().size() >= 2,
                        "NOT BETWEEN 需要提供两个值", HttpStatus.BAD_REQUEST);
                return columnRef + " NOT BETWEEN " + formatSqlValue(filter.getValues().get(0), effectiveType)
                        + " AND " + formatSqlValue(filter.getValues().get(1), effectiveType);
            case GT:
                return columnRef + " > " + formatSqlValue(filter.getValue(), effectiveType);
            case GE:
                return columnRef + " >= " + formatSqlValue(filter.getValue(), effectiveType);
            case LT:
                return columnRef + " < " + formatSqlValue(filter.getValue(), effectiveType);
            case LE:
                return columnRef + " <= " + formatSqlValue(filter.getValue(), effectiveType);
            case IS_NULL:
                return columnRef + " IS NULL";
            case IS_NOT_NULL:
                return columnRef + " IS NOT NULL";
            case APPLY:
                String template = filter.getValue() != null ? filter.getValue().toString() : "";
                String applySql = template.replace("{0}", columnRef);
                if (CollectionUtils.isNotEmpty(filter.getValues())) {
                    for (int i = 0; i < filter.getValues().size(); i++) {
                        String placeholder = "{" + (i + 1) + "}";
                        if (applySql.contains(placeholder)) {
                            applySql = applySql.replace(placeholder, formatSqlValue(filter.getValues().get(i), OntologyDataTypeEnum.String));
                        }
                    }
                }
                return applySql;
            default:
                return "";
        }
    }

    /**
     * 获取有效的类型：优先使用 filter 声明的 dataType，否则使用属性定义的类型。
     */
    private OntologyDataTypeEnum getEffectiveType(String dataType, OntologyDataTypeEnum propType) {
        if (StringUtils.isNotEmpty(dataType)) {
            switch (dataType.toUpperCase()) {
                case "NUMBER":
                    return OntologyDataTypeEnum.Double;
                case "BOOLEAN":
                    return OntologyDataTypeEnum.Bool;
                case "STRING":
                default:
                    return OntologyDataTypeEnum.String;
            }
        }
        return propType;
    }

    /** 数值字面量：允许符号、小数、科学计数，其余（含引号、分号）一律拒绝 */
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$");

    private String formatSqlValue(Object value, OntologyDataTypeEnum type) {
        if (value == null) {
            return "NULL";
        }
        if (type == OntologyDataTypeEnum.Float || type == OntologyDataTypeEnum.Double
                || type == OntologyDataTypeEnum.Long || type == OntologyDataTypeEnum.Int) {
            String strVal = value.toString();
            if (StringUtils.isBlank(strVal)) {
                throw new BusinessException("数值型过滤值不能为空", HttpStatus.BAD_REQUEST);
            }
            // 数值直接拼进 SQL 不加引号：外部输入的值也走到这里，不是合法数字就报错，别让内容进语句
            if (!NUMERIC_LITERAL.matcher(strVal.trim()).matches()) {
                throw new BusinessException("数值型过滤值不合法：" + strVal, HttpStatus.BAD_REQUEST);
            }
            return strVal.trim();
        }
        if (type == OntologyDataTypeEnum.Bool) {
            return Boolean.TRUE.equals(value) ? "TRUE" : "FALSE";
        }
        String escaped = value.toString().replace("'", "''");
        return StringUtils.wrap(escaped, "'");
    }


    private void deleteExistEntities(OntologyProperty pkProp) {
        /* 删除对象下已有实体
         *   1 删除属性，包括关联属性信息
         *   2 删除实体节点
         *   3 删除关系
         */

        var entityData = objectMapper.queryDataByPrimaryKeyList(pkProp.getDatasourceSchema(), pkProp.getDatasourceId(), Lists.newArrayList(pkProp.getDatasourceColumnName()), pkProp.getDatasourceColumnName(), null);
        //无实体数据
        if (CollectionUtils.isEmpty(entityData)) {
            return;
        }
        //删除实体主表
        objectMapper.deleteByTableName(pkProp.getDatasourceSchema(), pkProp.getDatasourceId());
        //删除实体属性关联表
        var tableFieldMappings = tableFieldMappingMapper.selectBySourceTableAndColumn(
                pkProp.getDatasourceSchema(), pkProp.getDatasourceId(), pkProp.getDatasourceColumnName());
        tableFieldMappings.stream().forEach(tableFieldMapping -> objectMapper.deleteByTableName(pkProp.getDatasourceSchema(), tableFieldMapping.getTargetTableName()));

        //删除实体关系和节点
        relationRepository.deleteRelationAndNodeByOntologyUniqueIdentifier(pkProp.getOntologyUniqueIdentifier());

    }

    @Override
    @Transactional(value = "datalakeTransactionManager")
    public void deleteDatalakeInstances(String ontologyUniqueIdentifier) {
        //定位主键属性，其 datasourceSchema/datasourceId/datasourceColumnName 指向实例所在的物理表
        var pkProp = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getIsPrimaryKey, 1)
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier))
                .stream().findFirst().orElse(null);
        if (pkProp == null
                || StringUtils.isEmpty(pkProp.getDatasourceId())
                || StringUtils.isEmpty(pkProp.getDatasourceSchema())
                || StringUtils.isEmpty(pkProp.getDatasourceColumnName())) {
            return;
        }
        deleteExistEntities(pkProp);
    }

    @Override
    public OntologyInstancesExportDTO exportInstances(String ontologyUniqueIdentifier) {
        var emptyResult = OntologyInstancesExportDTO.builder()
                .nodes(Lists.<EntityNodeExportDTO>newArrayList())
                .build();

        //仅取启用中且绑定了数据源列的属性
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                        .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()))
                .stream()
                .filter(v -> StringUtils.isNotEmpty(v.getDatasourceColumnName()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(props)) {
            return emptyResult;
        }

        //主键属性决定主表；不存在或未绑定数据源则不导实例
        var primaryKeyProp = props.stream()
                .filter(v -> v.getIsPrimaryKey() != null && v.getIsPrimaryKey() == 1)
                .findFirst();
        if (!primaryKeyProp.isPresent()) {
            return emptyResult;
        }
        var pk = primaryKeyProp.get();
        if (StringUtils.isEmpty(pk.getDatasourceId())
                || StringUtils.isEmpty(pk.getDatasourceSchema())
                || StringUtils.isEmpty(pk.getDatasourceColumnName())) {
            return emptyResult;
        }

        var mainSchema = pk.getDatasourceSchema();
        var mainTable = pk.getDatasourceId();
        var pkApiName = pk.getApiName();

        //标题键属性 apiName（用于 displayName），无则用主键值
        var titleApiName = props.stream()
                .filter(v -> v.getIsTitleKey() != null && v.getIsTitleKey() == 1)
                .map(OntologyProperty::getApiName)
                .findFirst().orElse(null);

        //属性按 datasourceId（物理表）分组
        var propsMap = props.stream().collect(Collectors.groupingBy(OntologyProperty::getDatasourceId));

        var exportNodes = Lists.<EntityNodeExportDTO>newArrayList();
        try {
            //主表全量：物理列名 -> 别名(apiName)
            var mainColumnToApiName = new LinkedHashMap<String, String>();
            propsMap.get(mainTable).forEach(p -> mainColumnToApiName.put(p.getDatasourceColumnName(), p.getApiName()));
            var mainRows = objectMapper.queryTableDataByColumn(mainSchema, mainTable, mainColumnToApiName, null);
            if (CollectionUtils.isEmpty(mainRows)) {
                return emptyResult;
            }

            //关联表：按关联键值分组缓存，避免逐行查库
            //结构：物理表名 -> (关联键值 -> List<行(apiName->值)>)
            var joinGroups = Maps.<String, Map<String, List<Map<String, Object>>>>newHashMap();
            //物理表名 -> 关联键列名
            var joinKeyByTable = Maps.<String, String>newHashMap();
            propsMap.forEach((table, tableProps) -> {
                if (StringUtils.equals(table, mainTable)) {
                    return;
                }
                var mapping = tableFieldMappingMapper.selectBySourceAndTarget(mainSchema, mainTable, table);
                if (mapping == null || StringUtils.isEmpty(mapping.getTargetColumnName())) {
                    return;
                }
                var joinKey = mapping.getTargetColumnName();
                //物理列名 -> 别名(apiName)，额外带上关联键列（用列名本身作别名）
                var columnToAlias = new LinkedHashMap<String, String>();
                tableProps.forEach(p -> columnToAlias.put(p.getDatasourceColumnName(), p.getApiName()));
                columnToAlias.put(joinKey, joinKey);
                var rows = objectMapper.queryTableDataByColumn(mainSchema, table, columnToAlias, null);
                if (CollectionUtils.isEmpty(rows)) {
                    return;
                }
                var grouped = rows.stream()
                        .filter(r -> r.get(joinKey) != null)
                        .collect(Collectors.groupingBy(r -> String.valueOf(r.get(joinKey))));
                joinGroups.put(table, grouped);
                joinKeyByTable.put(table, joinKey);
            });

            //内存 join：以主表行为骨架
            for (var row : mainRows) {
                var pkVal = row.get(pkApiName);
                var properties = Maps.<String, Object>newLinkedHashMap();
                //主表属性为标量
                properties.putAll(row);
                //关联表属性：按 storageGroup 组织为行对象列表
                Map<String, List<Map<String, Object>>> relatedTables = null;
                var pkKey = pkVal == null ? null : String.valueOf(pkVal);
                if (pkKey != null) {
                    for (var entry : joinGroups.entrySet()) {
                        var table = entry.getKey();
                        var grouped = entry.getValue();
                        var matched = grouped.get(pkKey);
                        if (CollectionUtils.isEmpty(matched)) {
                            continue;
                        }
                        var joinKey = joinKeyByTable.get(table);
                        var rowMaps = matched.stream().<Map<String, Object>>map(m -> {
                            Map<String, Object> rowMap = new LinkedHashMap<>();
                            m.forEach((k, v) -> {
                                if (!StringUtils.equals(k, joinKey)) {
                                    rowMap.put(k, v);
                                }
                            });
                            return rowMap;
                        }).collect(Collectors.toList());
                        if (relatedTables == null) {
                            relatedTables = Maps.newLinkedHashMap();
                        }
                        relatedTables.put(table, rowMaps);
                    }
                }
                var titleVal = titleApiName == null ? null : properties.get(titleApiName);
                var displayName = titleVal != null ? String.valueOf(titleVal)
                        : (pkVal == null ? null : String.valueOf(pkVal));
                exportNodes.add(EntityNodeExportDTO.builder()
                        .primaryKey(pkVal)
                        .displayName(displayName)
                        .properties(properties)
                        .relatedTables(relatedTables)
                        .build());
            }
        } catch (Exception e) {
            log.error("导出对象 {} 实例数据失败，数据源不可达或查询异常", ontologyUniqueIdentifier, e);
            return emptyResult;
        }

        return OntologyInstancesExportDTO.builder()
                .nodes(exportNodes)
                .build();
    }

    @Transactional(transactionManager = "datalakeTransactionManager")
    @Override
    public Map<String, Object> importInstances(String ontologyUniqueIdentifier, OntologyInstancesExportDTO instances) {
        var pkMapping = Maps.<String, Object>newLinkedHashMap();
        if (instances == null || CollectionUtils.isEmpty(instances.getNodes())) {
            return pkMapping;
        }
        var ontologyProperties = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier));
        if (CollectionUtils.isEmpty(ontologyProperties)) {
            log.warn("导入对象 {} 实例数据跳过：对象无属性", ontologyUniqueIdentifier);
            return pkMapping;
        }
        //主键属性 apiName：物理主键为 id SERIAL，导入时丢弃原值，由数据库重新生成
        var pkApiName = ontologyProperties.stream()
                .filter(p -> p.getIsPrimaryKey() != null && p.getIsPrimaryKey() == 1)
                .map(OntologyProperty::getApiName)
                .findFirst().orElse(null);
        //apiName -> 属性（仅取已绑定数据源列的），用于取 storageGroup
        var propertyMap = ontologyProperties.stream()
                .filter(p -> StringUtils.isNotEmpty(p.getDatasourceColumnName()))
                .collect(Collectors.toMap(OntologyProperty::getApiName, v -> v, (a, b) -> a));

        //保留导出节点与实体的配对：createEntities 返回的主键与入参同序，据此回传导出主键到新主键的映射
        var entities = Lists.<EntityCreateParam.Entity>newArrayList();
        var sourceNodes = Lists.<EntityNodeExportDTO>newArrayList();
        instances.getNodes().forEach(node -> {
            var entity = buildEntityFromNode(node, propertyMap, pkApiName);
            if (entity == null) {
                return;
            }
            entities.add(entity);
            sourceNodes.add(node);
        });
        if (CollectionUtils.isEmpty(entities)) {
            log.warn("导入对象 {} 实例数据跳过：无有效节点（属性未绑定数据源或缺少主存储分组属性）", ontologyUniqueIdentifier);
            return pkMapping;
        }
        //复用实体创建逻辑：写数据湖主表 + 关联表（id 由 SERIAL 生成）
        var newPrimaryKeys = createEntities(EntityCreateParam.builder()
                .ontologyIdentifier(ontologyUniqueIdentifier)
                .entityList(entities)
                .build());
        for (var i = 0; i < entities.size() && i < newPrimaryKeys.size(); i++) {
            var sourceKey = pkKey(sourceNodes.get(i).getPrimaryKey());
            if (sourceKey != null) {
                pkMapping.put(sourceKey, newPrimaryKeys.get(i));
            }
        }
        saveImportedEntityNodes(ontologyUniqueIdentifier, sourceNodes, newPrimaryKeys);
        return pkMapping;
    }

    /**
     * 实例落库后直接用新主键补齐图库节点，不等后续的 createEntityNodes 回查数据湖物理表。
     *
     * <p>实例关系边必须挂在图库已有的 {@code node} 上，而回查依赖数据湖可见性：同一次导入请求里刚写入的
     * 实例行未必查得到，节点建不出来，边就全部跳过。主键类型与 {@code syncNodes} 对齐（Integer 归一为 Long），
     * 避免两条路径为同一实例建出重复节点。</p>
     */
    private void saveImportedEntityNodes(String ontologyUniqueIdentifier,
                                         List<EntityNodeExportDTO> sourceNodes,
                                         List<Object> newPrimaryKeys) {
        if (CollectionUtils.isEmpty(sourceNodes) || CollectionUtils.isEmpty(newPrimaryKeys)) {
            return;
        }
        var primaryProperty = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .eq(OntologyProperty::getIsPrimaryKey, 1));
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceId())) {
            log.warn("导入对象 {} 图库节点跳过：主键属性未绑定数据源", ontologyUniqueIdentifier);
            return;
        }
        var existPks = getByByOntologyUniqIdentifier(ontologyUniqueIdentifier).stream()
                .map(node -> pkKey(node.getPrimaryKey())).collect(Collectors.toSet());
        var nodes = Lists.<EntityNode>newArrayList();
        var size = Math.min(sourceNodes.size(), newPrimaryKeys.size());
        for (var i = 0; i < size; i++) {
            var primaryKey = newPrimaryKeys.get(i);
            if (primaryKey == null) {
                continue;
            }
            if (primaryKey instanceof Integer) {
                primaryKey = Long.parseLong(primaryKey.toString());
            }
            if (existPks.contains(pkKey(primaryKey))) {
                continue;
            }
            nodes.add(EntityNode.builder()
                    .ontologyUniqIdentifier(ontologyUniqueIdentifier)
                    .primaryKey(primaryKey)
                    .tableName(primaryProperty.getDatasourceId())
                    .displayName(StringUtils.defaultIfBlank(sourceNodes.get(i).getDisplayName(), pkKey(primaryKey)))
                    .build());
        }
        if (CollectionUtils.isNotEmpty(nodes)) {
            nodeRepository.batchSave(nodes);
        }
        log.info("导入对象 {} 图库节点补齐：实例 {} 个，新建节点 {} 个", ontologyUniqueIdentifier, size, nodes.size());
    }

    /**
     * 将导出节点（扁平 apiName->值）还原为实体创建入参。
     *
     * <p>丢弃主键 id（由数据库 SERIAL 重新生成）与未绑定数据源/未知属性；main 组的 null 值一并丢弃
     * （规避 createEntities 主表插入 Collectors.toMap 对 null 值抛 NPE）。按 storageGroup 分组：
     * main 组还原为单行，非 main 组按 List 下标还原为一对多多行。</p>
     *
     * @return 无可导入的 main 组属性时返回 null（该节点跳过）
     */
    private EntityCreateParam.Entity buildEntityFromNode(EntityNodeExportDTO node,
                                                         Map<String, OntologyProperty> propertyMap,
                                                         String pkApiName) {
        var properties = node.getProperties();
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        //main 组属性：从 properties 中取，丢弃主键、未知/未绑定属性
        var mainValues = Maps.<String, Object>newLinkedHashMap();
        properties.forEach((apiName, value) -> {
            if (StringUtils.equals(apiName, pkApiName)) {
                return;
            }
            var prop = propertyMap.get(apiName);
            if (prop == null) {
                return;
            }
            var group = StringUtils.isEmpty(prop.getStorageGroup()) ? "main" : prop.getStorageGroup();
            if (!"main".equals(group)) {
                return;
            }
            mainValues.put(apiName, value);
        });

        var mainInfos = toPropertyInfos(mainValues, propertyMap, true);
        if (CollectionUtils.isEmpty(mainInfos)) {
            log.warn("导入实例节点跳过：无主存储分组(main)有效属性，primaryKey={}", node.getPrimaryKey());
            return null;
        }
        var entityProperties = Lists.<EntityCreateParam.EntityProperty>newArrayList();
        var mainRows = Lists.<List<EntityCreateParam.PropertyInfo>>newArrayList();
        mainRows.add(mainInfos);
        entityProperties.add(EntityCreateParam.EntityProperty.builder()
                .storageGroup("main")
                .props(mainRows)
                .build());

        //关联表数据：优先取 relatedTables（新格式：行对象列表），否则从 properties 里还原（旧格式：平行数组）
        var relatedTables = node.getRelatedTables();
        if (relatedTables != null && !relatedTables.isEmpty()) {
            // relatedTables 的键导出侧写的是物理表名、旧数据里也可能是存储分组名，归组一律按行内属性在本对象上的 storageGroup 取
            var rowsByGroup = Maps.<String, List<List<EntityCreateParam.PropertyInfo>>>newLinkedHashMap();
            relatedTables.forEach((tableKey, rows) -> {
                if (CollectionUtils.isEmpty(rows)) {
                    return;
                }
                for (var rowMap : rows) {
                    var rowInfos = toPropertyInfos(rowMap, propertyMap, false);
                    if (CollectionUtils.isEmpty(rowInfos)) {
                        continue;
                    }
                    rowsByGroup.computeIfAbsent(resolveRelatedStorageGroup(rowMap, propertyMap, tableKey),
                            k -> Lists.newArrayList()).add(rowInfos);
                }
            });
            rowsByGroup.forEach((storageGroup, propRows) -> entityProperties.add(
                    EntityCreateParam.EntityProperty.builder()
                            .storageGroup(storageGroup)
                            .props(propRows)
                            .build()));
        } else {
            //旧格式兼容：非 main 组属性散落在 properties 里，值为 List 或标量
            var groupedValues = Maps.<String, Map<String, Object>>newLinkedHashMap();
            properties.forEach((apiName, value) -> {
                if (StringUtils.equals(apiName, pkApiName)) {
                    return;
                }
                var prop = propertyMap.get(apiName);
                if (prop == null) {
                    return;
                }
                var group = StringUtils.isEmpty(prop.getStorageGroup()) ? "main" : prop.getStorageGroup();
                if ("main".equals(group)) {
                    return;
                }
                groupedValues.computeIfAbsent(group, k -> Maps.newLinkedHashMap()).put(apiName, value);
            });
            groupedValues.forEach((group, values) -> {
                var rows = buildRelatedRows(values, propertyMap);
                if (CollectionUtils.isNotEmpty(rows)) {
                    entityProperties.add(EntityCreateParam.EntityProperty.builder()
                            .storageGroup(group)
                            .props(rows)
                            .build());
                }
            });
        }

        return EntityCreateParam.Entity.builder()
                .entityProperties(entityProperties)
                .build();
    }

    /**
     * 取关联表行所属的存储分组名：行内 apiName 在本对象属性上能查到分组就用分组名，
     * 全部查不到（属性未绑定数据源列）时回退用 relatedTables 原键。
     */
    private String resolveRelatedStorageGroup(Map<String, Object> rowMap,
                                              Map<String, OntologyProperty> propertyMap,
                                              String defaultGroup) {
        for (var apiName : rowMap.keySet()) {
            var prop = propertyMap.get(apiName);
            if (prop != null && StringUtils.isNotEmpty(prop.getStorageGroup())
                    && !"main".equals(prop.getStorageGroup())) {
                return prop.getStorageGroup();
            }
        }
        return defaultGroup;
    }

    /**
     * 属性值映射转 PropertyInfo 列表。
     *
     * @param skipNull 为 true 时跳过 null 值（main 组插入用 Collectors.toMap，null 值会 NPE）
     */
    private List<EntityCreateParam.PropertyInfo> toPropertyInfos(Map<String, Object> values, 
                                                                 Map<String, OntologyProperty> propertyMap,
                                                                 boolean skipNull) {
        if (values == null || values.isEmpty()) {
            return Lists.newArrayList();
        }
        return values.entrySet().stream()
                .filter(e -> !(skipNull && e.getValue() == null))
                .map(e -> {
                    var prop = propertyMap.get(e.getKey());
                    var convertedValue = e.getValue();
                    if (prop != null && prop.getPropertyType() != null && convertedValue != null) {
                        try {
                            convertedValue = DataConverter.convert2DataType(convertedValue, prop.getPropertyType());
                        } catch (Exception ex) {
                            log.warn("属性 {} 类型转换失败，保留原值：{}", e.getKey(), ex.getMessage());
                        }
                    }
                    return EntityCreateParam.PropertyInfo.builder()
                            .propertyApiName(e.getKey())
                            .propertyValue(convertedValue)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 关联表（非 main 存储分组）属性值还原为多行：List 值按下标展开，标量值在各行重复；
     * 行数取该组内 List 的最大长度（至少 1）。
     */
    private List<List<EntityCreateParam.PropertyInfo>> buildRelatedRows(Map<String, Object> values,
                                                                        Map<String, OntologyProperty> propertyMap) {
        //apiName -> 归一化后的值列表
        var normalized = Maps.<String, List<Object>>newLinkedHashMap();
        var rowCount = 1;
        for (var e : values.entrySet()) {
            var list = new ArrayList<Object>();
            if (e.getValue() instanceof List) {
                for (var o : (List<?>) e.getValue()) {
                    list.add(o);
                }
            } else {
                list.add(e.getValue());
            }
            normalized.put(e.getKey(), list);
            rowCount = Math.max(rowCount, list.size());
        }
        var rows = Lists.<List<EntityCreateParam.PropertyInfo>>newArrayList();
        for (int i = 0; i < rowCount; i++) {
            var row = Lists.<EntityCreateParam.PropertyInfo>newArrayList();
            for (var e : normalized.entrySet()) {
                var list = e.getValue();
                Object v;
                if (list.size() > i) {
                    v = list.get(i);
                } else if (list.size() == 1) {
                    //标量在多行间重复
                    v = list.get(0);
                } else {
                    v = null;
                }
                //类型转换
                var prop = propertyMap.get(e.getKey());
                if (prop != null && prop.getPropertyType() != null && v != null) {
                    try {
                        v = DataConverter.convert2DataType(v, prop.getPropertyType());
                    } catch (Exception ex) {
                        log.warn("属性 {} 类型转换失败，保留原值：{}", e.getKey(), ex.getMessage());
                    }
                }
                row.add(EntityCreateParam.PropertyInfo.builder()
                        .propertyApiName(e.getKey())
                        .propertyValue(v)
                        .build());
            }
            rows.add(row);
        }
        return rows;
    }

    // ────────────────── 实例数据属性查询（带条件校验） ──────────────────

    @Override
    public Page<List<EntityPropertyGenericQueryVO>> queryInstancePropertyData(EntityInstanceDataQueryParam param) {
        // 1. 查询该对象的所有属性
        var props = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyUniqueIdentifier()));
        var propMap = props.stream().collect(Collectors.toMap(OntologyProperty::getApiName, v -> v));

        // 2. 校验返回属性：必须存在且已关联数据源
        for (String apiName : param.getPropertyApiNames()) {
            var prop = propMap.get(apiName);
            PreconditionUtils.checkArgument(prop != null && StringUtils.isNotEmpty(prop.getDatasourceId()),
                    "属性 " + apiName + " 不存在或未关联数据源", HttpStatus.BAD_REQUEST);
        }

        // 3. 校验过滤条件（属性存在性 + 类型兼容性），不通过则直接抛异常
        if (param.getFilters() != null) {
            validateFilterGroup(param.getFilters(), propMap);
        }

        // 4. 转换为 EntityPropertyGenericQueryParam，复用已有查询逻辑
        var genericParam = new EntityPropertyGenericQueryParam();
        genericParam.setOntologyIdentifier(param.getOntologyUniqueIdentifier());
        genericParam.setSelectProperties(param.getPropertyApiNames().stream()
                .map(name -> OntologySelectPropertyParam.builder().propertyApiName(name).build())
                .collect(Collectors.toList()));
        genericParam.setFilters(param.getFilters());
        genericParam.setPageNum(param.getPageNum());
        genericParam.setPageSize(param.getPageSize());

        return genericQuery(genericParam);
    }

    /**
     * 递归校验过滤条件树：每个 filter 节点的属性必须属于该对象，
     * 值类型必须与属性定义的类型兼容。
     */
    private void validateFilterGroup(FilterGroupParam group, Map<String, OntologyProperty> propMap) {
        if (group == null || CollectionUtils.isEmpty(group.getChildren())) {
            return;
        }
        for (FilterNodeParam node : group.getChildren()) {
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                var filter = node.getFilter();
                PreconditionUtils.checkArgument(filter.getSource() != FilterValueSourceEnum.EXTERNAL,
                        "过滤条件中的属性 " + filter.getPropertyApiName()
                                + " 声明了外部输入，只有基础函数行为支持外部输入条件", HttpStatus.BAD_REQUEST);
                var prop = propMap.get(filter.getPropertyApiName());
                PreconditionUtils.checkArgument(prop != null,
                        "过滤条件中的属性 " + filter.getPropertyApiName() + " 不属于该对象", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(StringUtils.isNotEmpty(prop.getDatasourceId()),
                        "过滤条件中的属性 " + filter.getPropertyApiName() + " 未关联数据源", HttpStatus.BAD_REQUEST);

                OntologyDataTypeEnum propType = prop.getPropertyType();
                QueryOpEnum op = filter.getOp() != null ? filter.getOp() : QueryOpEnum.EQ;

                // IS_NULL / IS_NOT_NULL 不需要校验值
                if (op == QueryOpEnum.IS_NULL || op == QueryOpEnum.IS_NOT_NULL) {
                    continue;
                }

                // LIKE 类操作只适用于字符串类型
                if (op == QueryOpEnum.LIKE || op == QueryOpEnum.LIKE_LEFT || op == QueryOpEnum.LIKE_RIGHT) {
                    PreconditionUtils.checkArgument(propType == OntologyDataTypeEnum.String,
                            "属性 " + filter.getPropertyApiName() + " 类型为 " + propType.getValue() + "，不支持 " + op + " 操作",
                            HttpStatus.BAD_REQUEST);
                }

                // 校验单值
                if (filter.getValue() != null) {
                    validateFilterValue(filter.getPropertyApiName(), filter.getValue(), propType, op);
                }
                // 校验多值（IN / BETWEEN）
                if (CollectionUtils.isNotEmpty(filter.getValues())) {
                    for (Object v : filter.getValues()) {
                        validateFilterValue(filter.getPropertyApiName(), v, propType, op);
                    }
                }
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                validateFilterGroup(node.getGroup(), propMap);
            }
        }
    }

    /**
     * 校验单个过滤值与属性类型是否兼容：
     * 数值类型必须可解析为数字，日期/时间戳类型必须可解析，布尔类型必须为 true/false。
     */
    private void validateFilterValue(String apiName, Object value, OntologyDataTypeEnum propType, QueryOpEnum op) {
        if (value == null) {
            return;
        }
        String strVal = value.toString();
        try {
            switch (propType) {
                case Int:
                    Integer.parseInt(strVal);
                    break;
                case Long:
                    Long.parseLong(strVal);
                    break;
                case Float:
                    Float.parseFloat(strVal);
                    break;
                case Double:
                case Decimal:
                    Double.parseDouble(strVal);
                    break;
                case Bool:
                    if (!"true".equalsIgnoreCase(strVal) && !"false".equalsIgnoreCase(strVal)) {
                        throw new BusinessException(
                                "属性 " + apiName + " 类型为 Boolean，值 \"" + strVal + "\" 不合法，应为 true 或 false",
                                HttpStatus.BAD_REQUEST);
                    }
                    break;
                case Date:
                    java.time.LocalDate.parse(strVal);
                    break;
                case Timestamp:
                    java.time.LocalDateTime.parse(strVal);
                    break;
                case String:
                    // 字符串类型接受任何值
                    break;
                default:
                    // 其他复杂类型（Array/Map/Ontology 等）不做比较操作，拒绝 GT/LT/GE/LE/BETWEEN
                    if (op == QueryOpEnum.GT || op == QueryOpEnum.GE || op == QueryOpEnum.LT
                            || op == QueryOpEnum.LE || op == QueryOpEnum.BETWEEN || op == QueryOpEnum.NOT_BETWEEN) {
                        throw new BusinessException(
                                "属性 " + apiName + " 类型为 " + propType.getValue() + "，不支持 " + op + " 比较操作",
                                HttpStatus.BAD_REQUEST);
                    }
                    break;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    "属性 " + apiName + " 类型为 " + propType.getValue() + "，值 \"" + strVal + "\" 无法解析",
                    HttpStatus.BAD_REQUEST);
        }
    }


}

