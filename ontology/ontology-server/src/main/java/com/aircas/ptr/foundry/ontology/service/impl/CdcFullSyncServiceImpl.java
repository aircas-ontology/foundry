package com.aircas.ptr.foundry.ontology.service.impl;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.util.ObjectBuilder;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.converter.OntologyInstanceConverter;
import com.aircas.ptr.foundry.ontology.converter.OntologyMetaConverter;
import com.aircas.ptr.foundry.ontology.converter.OntologyPropertyConverter;
import com.aircas.ptr.foundry.ontology.converter.OntologySpaceConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyInstanceDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyMetaDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologySpaceDTO;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.CdcFullSyncParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.CdcFullSyncResultVO;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyInstanceRepository;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyMetaRepository;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyPropertyRepository;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologySpaceRepository;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import com.aircas.ptr.foundry.ontology.service.CdcFullSyncService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CDC 初始化全量同步实现，见 {@link CdcFullSyncService}。
 * <p>
 * 幂等性设计：
 * <ul>
 *   <li>元数据索引（space/meta/property）：文档 _id = 数据库主键 id，saveAll 即 upsert，重复执行结果不变；</li>
 *   <li>实例索引：文档 _id = pk（{schema}.{table}.{ontology_uid}.{主键值}），同样 upsert。
 *       每行以整行内容重建文档（search_text 覆盖全列），不依赖增量事件的部分字段语义，
 *       重建结果与逐条快照写入等价；</li>
 *   <li>孤儿清理：以"数据库当前内容"为准对 ES 存量求差集删除（行被物理删除、表被删除、
 *       本体被禁用、空间 apiName 变更等场景均收敛），保证同步后 ES 与 DB 一致；</li>
 *   <li>并发保护：AtomicBoolean 互斥，同一实例同时只允许一个全量同步在跑，重复触发直接 409。</li>
 * </ul>
 * 同步直接读库写 ES，不经过 Kafka，与 CDC 消费组无关，可安全地与实时增量交错执行
 * （双方都是按 _id upsert，最终一致）。
 */
@Slf4j
@Service
public class CdcFullSyncServiceImpl implements CdcFullSyncService {

    /** 数据湖分页读取批大小 */
    private static final int PAGE_SIZE = 1000;
    /** 单次 terms 删除的最大项数 */
    private static final int TERMS_CHUNK = 1000;
    /** 孤儿扫描时一次性拉取的文档上限（超出则截断并告警） */
    private static final int MAX_SCAN_SIZE = 10000;

    /** 全量同步互斥标记（同一进程内防并发触发） */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    private OntologySpaceMapper spaceMapper;
    @Autowired
    private OntologyMetaMapper metaMapper;
    @Autowired
    private OntologyPropertyMapper propertyMapper;
    @Autowired
    private ObjectMapper lakeObjectMapper;
    @Autowired
    private TableMetadataMapper tableMetadataMapper;
    @Autowired
    private OntologySpaceRepository spaceRepository;
    @Autowired
    private OntologyMetaRepository metaRepository;
    @Autowired
    private OntologyPropertyRepository propertyRepository;
    @Autowired
    private OntologyInstanceRepository instanceRepository;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public CdcFullSyncResultVO fullSync(CdcFullSyncParam param) {
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException("全量同步正在进行中，请稍后再试",
                    ResultCode.DUPLICATION, HttpStatus.CONFLICT);
        }
        try {
            CdcFullSyncParam options = param == null ? new CdcFullSyncParam() : param;
            long start = System.currentTimeMillis();
            CdcFullSyncResultVO.CdcFullSyncResultVOBuilder result = CdcFullSyncResultVO.builder();

            if (!Boolean.FALSE.equals(options.getSyncSpace())) {
                // ontology_space 无软删 status 列（空间为物理删除），statusCol 传 null
                long[] r = syncIndex(spaceMapper, null, OntologySpace::getId,
                        OntologySpaceConverter::convert, spaceRepository, EsOntologySpaceDTO::getId,
                        "ontology_space");
                result.spaceSynced(r[0]).spaceDeleted(r[1]);
            }
            if (!Boolean.FALSE.equals(options.getSyncMeta())) {
                long[] r = syncIndex(metaMapper, OntologyMeta::getStatus, OntologyMeta::getId,
                        OntologyMetaConverter::convert, metaRepository, EsOntologyMetaDTO::getId,
                        "ontology_meta");
                result.metaSynced(r[0]).metaDeleted(r[1]);
            }
            if (!Boolean.FALSE.equals(options.getSyncProperty())) {
                long[] r = syncIndex(propertyMapper, OntologyProperty::getStatus, OntologyProperty::getId,
                        OntologyPropertyConverter::convert, propertyRepository, EsOntologyPropertyDTO::getId,
                        "ontology_property");
                result.propertySynced(r[0]).propertyDeleted(r[1]);
            }
            if (!Boolean.FALSE.equals(options.getSyncInstance())) {
                long[] r = syncInstanceIndex(options.getOntologyUids());
                result.instanceSynced(r[0]).instanceDeleted(r[1]);
            }

            refreshAllIndices();
            CdcFullSyncResultVO vo = result.costMillis(System.currentTimeMillis() - start).build();
            log.info("CDC 全量同步完成: {}", vo);
            return vo;
        } finally {
            running.set(false);
        }
    }

    // ------------------------------------------------------------------
    // 主库元数据索引：space / meta / property
    // ------------------------------------------------------------------

    /**
     * 通用"主库表 -> ES 索引"全量同步：全表按 _id upsert + 孤儿差集删除。
     * <p>
     * 软删除（status=-1）的行：Debezium 快照会原样导出，但业务上该记录已判定删除、
     * 增量删除链路会为其产生 delete 事件。为与增量链路最终一致，此处排除 status=DELETE 的行，
     * 其存量 ES 文档作为孤儿被差集清理删除。
     *
     * @return [写入文档数, 清理孤儿文档数]
     */
    private <P, D, I> long[] syncIndex(BaseMapper<P> mapper,
                                       SFunction<P, ?> statusCol,
                                       SFunction<P, ?> idCol,
                                       Function<P, D> converter,
                                       ElasticsearchRepository<D, I> repository,
                                       Function<D, I> docIdGetter,
                                       String indexName) {
        LambdaQueryWrapper<P> wrapper = new LambdaQueryWrapper<>();
        if (statusCol != null) {
            wrapper.ne(statusCol, Status.DELETE.getValue());
        }
        wrapper.orderByAsc(idCol);
        List<P> rows = mapper.selectList(wrapper);
        List<D> docs = rows.stream().map(converter).filter(Objects::nonNull).collect(Collectors.toList());
        repository.saveAll(docs);

        Set<I> expectedIds = docs.stream().map(docIdGetter).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<I> orphanIds = new ArrayList<>();
        for (D esDoc : repository.findAll()) {
            I id = docIdGetter.apply(esDoc);
            if (id != null && !expectedIds.contains(id)) {
                orphanIds.add(id);
            }
        }
        orphanIds.forEach(repository::deleteById);
        log.info("全量同步[{}]: 写入 {} 条, 清理孤儿 {} 条", indexName, docs.size(), orphanIds.size());
        return new long[]{docs.size(), orphanIds.size()};
    }

    // ------------------------------------------------------------------
    // 数据湖实例索引 ontology_instance
    // ------------------------------------------------------------------

    /**
     * @return [写入文档数, 清理孤儿文档数]
     */
    private long[] syncInstanceIndex(List<String> ontologyUidFilter) {
        // 启用中的本体：uid -> meta（实例同步范围，与 CDC 消费端 syncInstance 的过滤口径一致）
        Map<String, OntologyMeta> enabledMetas = metaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream()
                .filter(m -> StringUtils.isNotEmpty(m.getUniqueIdentifier()))
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, m -> m, (a, b) -> a, LinkedHashMap::new));
        // 指定本体过滤（缺省全部启用本体）。带过滤时跳过全局孤儿清理，避免误删过滤范围外的合法文档；
        // 本体维度的孤儿清理仍会执行。
        boolean filtered = !CollectionUtils.isEmpty(ontologyUidFilter);
        if (filtered) {
            enabledMetas.keySet().retainAll(new HashSet<>(ontologyUidFilter));
        }

        // 本体所属空间：spaceId -> space（数据湖真实 schema = space.apiName）
        Map<Integer, OntologySpace> spaceById = new HashMap<>();
        enabledMetas.values().stream()
                .map(OntologyMeta::getOntologySpaceId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(spaceId -> {
                    OntologySpace space = spaceMapper.selectById(spaceId);
                    if (space != null) {
                        spaceById.put(spaceId, space);
                    }
                });

        long synced = 0;
        long deleted = 0;
        for (OntologyMeta meta : enabledMetas.values()) {
            OntologySpace space = meta.getOntologySpaceId() == null
                    ? null : spaceById.get(meta.getOntologySpaceId());
            String schema = space == null ? null : space.getApiName();
            if (StringUtils.isEmpty(schema)) {
                log.warn("本体[{}]未关联空间或缺少 apiName，跳过其实例同步", meta.getUniqueIdentifier());
                continue;
            }
            long[] tableResult = syncInstancesForOntology(meta, space, schema);
            synced += tableResult[0];
            deleted += tableResult[1];
        }

        // 全局孤儿清理：本体被禁用/删除、空间 apiName 变更等导致"按本体维度已无法覆盖"的残留文档
        if (!filtered) {
            deleted += sweepGlobalInstanceOrphans(enabledMetas, spaceById);
        }
        return new long[]{synced, deleted};
    }

    /**
     * 单个本体维度：找出其全部主表（主键属性数据源表），分页读取行数据 upsert 实例文档，
     * 再按"该本体现有文档 pk 集合 vs 本次期望 pk 集合"差集清理孤儿。
     * <p>
     * 注意：不能按 {@code ontology_property.datasource_schema} 过滤（自动绑定路径不回填该列，
     * 停留在默认值 'public' 与真实 schema 不符），真实 schema 以所属空间 apiName 为准——
     * 与 {@code EntityInstanceCdcJobServiceImpl#findPrimaryKeyProps} 的判定口径一致。
     *
     * @return [写入文档数, 清理孤儿文档数]
     */
    private long[] syncInstancesForOntology(OntologyMeta meta, OntologySpace space, String schema) {
        List<OntologyProperty> pkProps = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, meta.getUniqueIdentifier())
                .eq(OntologyProperty::getIsPrimaryKey, 1)
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()));
        // 主键属性按数据源表名分组；datasource_id 即表名
        Map<String, List<OntologyProperty>> propsByTable = pkProps.stream()
                .filter(p -> StringUtils.isNotEmpty(p.getDatasourceId()))
                .collect(Collectors.groupingBy(OntologyProperty::getDatasourceId, LinkedHashMap::new,
                        Collectors.toList()));

        long synced = 0;
        Set<String> expectedPks = new HashSet<>();
        for (Map.Entry<String, List<OntologyProperty>> entry : propsByTable.entrySet()) {
            String table = entry.getKey();
            if (!Boolean.TRUE.equals(tableMetadataMapper.isTableExist(schema, table))) {
                log.warn("本体[{}]主表[{}.{}]不存在，跳过（其存量实例文档将由孤儿清理删除）",
                        meta.getUniqueIdentifier(), schema, table);
                continue;
            }
            // 名称键与主键同表（业务约束），缺省时实例 name 取主键值（由转换器兜底）
            OntologyProperty titleProp = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                            .eq(OntologyProperty::getOntologyUniqueIdentifier, meta.getUniqueIdentifier())
                            .eq(OntologyProperty::getIsTitleKey, 1)
                            .eq(OntologyProperty::getDatasourceId, table)
                            .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()))
                    .stream().findFirst().orElse(null);
            // 同表配置多个主键属性时取其一（_id 含 ontology_uid，不同属性定位的是同一行，不会冲突）
            String pkColumn = entry.getValue().get(0).getDatasourceColumnName();
            synced += pageAndUpsertRows(meta, space, schema, table, pkColumn, titleProp, expectedPks);
        }

        long deleted = deleteInstanceOrphans(meta.getUniqueIdentifier(), expectedPks);
        return new long[]{synced, deleted};
    }

    /**
     * 分页读取数据湖表并逐批 upsert 实例文档。
     * 无 ORDER BY 的分页在并发写入下理论上可能漏读/重读：重读因按 _id upsert 无害；
     * 漏读行由全局孤儿清理兜底（下次触发全量同步即可收敛）。
     *
     * @return 写入文档数
     */
    private long pageAndUpsertRows(OntologyMeta meta, OntologySpace space, String schema, String table,
                                   String pkColumn, OntologyProperty titleProp, Set<String> expectedPks) {
        // 与 CDC 消费端（EntityDatasourceDMLListener 链路）一致：表含 is_deleted 软删列时只同步未删除行
        boolean hasDeletedField = Boolean.TRUE.equals(
                tableMetadataMapper.isColumnExist(schema, table, "is_deleted"));
        long synced = 0;
        int offset = 0;
        while (true) {
            List<Map<String, Object>> rows = lakeObjectMapper.queryMapsPage(
                    schema, table, null, null, null, PAGE_SIZE, offset, hasDeletedField);
            if (CollectionUtils.isEmpty(rows)) {
                break;
            }
            List<EsOntologyInstanceDTO> batch = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                Object pkValue = OntologyInstanceConverter.getColumnValue(row, pkColumn);
                if (pkValue == null) {
                    log.warn("表[{}.{}]存在主键列[{}]为空的行，跳过", schema, table, pkColumn);
                    continue;
                }
                Object titleValue = titleProp == null
                        ? null : OntologyInstanceConverter.getColumnValue(row, titleProp.getDatasourceColumnName());
                EsOntologyInstanceDTO dto = OntologyInstanceConverter.convert(
                        schema, table, pkValue, titleValue, row, meta, space);
                if (dto == null) {
                    continue;
                }
                batch.add(dto);
                expectedPks.add(dto.getPk());
            }
            if (!batch.isEmpty()) {
                instanceRepository.saveAll(batch);
                synced += batch.size();
            }
            if (rows.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
        log.info("数据湖[{}.{}]本体[{}]实例全量写入 {} 条", schema, table, meta.getUniqueIdentifier(), synced);
        return synced;
    }

    /**
     * 本体维度孤儿清理：删除该 ontology_uid 下 pk 不在期望集合中的实例文档。
     */
    private long deleteInstanceOrphans(String ontologyUid, Set<String> expectedPks) {
        List<String> existingPks = fetchInstanceDocs(
                q -> q.term(t -> t.field("ontology_uid").value(ontologyUid)))
                .stream().map(EsOntologyInstanceDTO::getPk).collect(Collectors.toList());
        List<String> orphans = existingPks.stream()
                .filter(pk -> pk != null && !expectedPks.contains(pk))
                .collect(Collectors.toList());
        return deleteInstancesByPks(orphans);
    }

    /**
     * 全局孤儿清理：逐篇校验实例文档仍可满足同步条件——所属本体启用、所属空间存在且
     * 文档 pk 以当前 schema（空间 apiName）为前缀（apiName 变更后旧 pk 文档即孤儿）。
     */
    private long sweepGlobalInstanceOrphans(Map<String, OntologyMeta> enabledMetas,
                                            Map<Integer, OntologySpace> spaceById) {
        List<EsOntologyInstanceDTO> docs = fetchInstanceDocs(q -> q.matchAll(m -> m));
        List<String> orphans = new ArrayList<>();
        for (EsOntologyInstanceDTO doc : docs) {
            if (StringUtils.isEmpty(doc.getPk())) {
                continue;
            }
            OntologyMeta meta = doc.getOntologyUid() == null
                    ? null : enabledMetas.get(doc.getOntologyUid());
            if (meta == null) {
                orphans.add(doc.getPk());
                continue;
            }
            OntologySpace space = meta.getOntologySpaceId() == null
                    ? null : spaceById.get(meta.getOntologySpaceId());
            String schema = space == null ? null : space.getApiName();
            if (StringUtils.isEmpty(schema) || !doc.getPk().startsWith(schema + ".")) {
                orphans.add(doc.getPk());
            }
        }
        long deleted = deleteInstancesByPks(orphans);
        if (deleted > 0) {
            log.info("实例索引全局孤儿清理: {} 条", deleted);
        }
        return deleted;
    }

    /** 按 pk（= _id）分批 terms 删除实例文档（by-query 自动携带 routing，满足 _routing.required） */
    private long deleteInstancesByPks(List<String> pks) {
        if (CollectionUtils.isEmpty(pks)) {
            return 0;
        }
        long deleted = 0;
        for (int i = 0; i < pks.size(); i += TERMS_CHUNK) {
            List<String> chunk = pks.subList(i, Math.min(i + TERMS_CHUNK, pks.size()));
            List<FieldValue> values = chunk.stream().map(FieldValue::of).collect(Collectors.toList());
            DeleteQuery deleteQuery = DeleteQuery.builder(NativeQuery.builder()
                            .withQuery(q -> q.terms(t -> t.field("pk").terms(v -> v.value(values))))
                            .build())
                    .withRefresh(Boolean.TRUE)
                    .build();
            var response = elasticsearchOperations.delete(deleteQuery, EsOntologyInstanceDTO.class);
            deleted += response.getDeleted();
        }
        return deleted;
    }

    /** 按查询拉取实例文档（单次上限 MAX_SCAN_SIZE，超出告警） */
    private List<EsOntologyInstanceDTO> fetchInstanceDocs(
            Function<Query.Builder, ObjectBuilder<Query>> queryFn) {
        NativeQuery query = NativeQuery.builder().withQuery(queryFn).build();
        query.setMaxResults(MAX_SCAN_SIZE);
        List<EsOntologyInstanceDTO> docs = elasticsearchOperations
                .search(query, EsOntologyInstanceDTO.class)
                .getSearchHits().stream()
                .map(hit -> hit.getContent())
                .collect(Collectors.toList());
        if (docs.size() >= MAX_SCAN_SIZE) {
            log.warn("实例文档拉取达到上限 {}，超出部分本次不纳入孤儿清理", MAX_SCAN_SIZE);
        }
        return docs;
    }

    /** 同步结束后统一 refresh，保证立即可检索（与 CDC 链路的 IMMEDIATE refresh 语义对齐） */
    private void refreshAllIndices() {
        refreshIndexIfPresent(EsOntologySpaceDTO.class);
        refreshIndexIfPresent(EsOntologyMetaDTO.class);
        refreshIndexIfPresent(EsOntologyPropertyDTO.class);
        refreshIndexIfPresent(EsOntologyInstanceDTO.class);
    }

    private void refreshIndexIfPresent(Class<?> entityClass) {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(entityClass);
            if (indexOps.exists()) {
                indexOps.refresh();
            }
        } catch (Exception e) {
            log.warn("索引 refresh 失败: {}", e.getMessage());
        }
    }
}
