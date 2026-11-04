package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.converter.OntologyInstanceConverter;
import com.aircas.ptr.foundry.ontology.converter.OntologyLinkGroupConverter;
import com.aircas.ptr.foundry.ontology.converter.OntologyMetaConverter;
import com.aircas.ptr.foundry.ontology.converter.OntologyPropertyConverter;
import com.aircas.ptr.foundry.ontology.converter.OntologySpaceConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyInstanceDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyLinkGroupDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyMetaDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologySpaceDTO;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.CdcFullSyncParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.CdcFullSyncResultVO;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyInstanceRepository;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyLinkGroupRepository;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyMetaRepository;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyPropertyRepository;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologySpaceRepository;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import com.aircas.ptr.foundry.ontology.service.CdcIncrementalSyncService;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CDC 增量同步实现，见 {@link CdcIncrementalSyncService}。
 * <p>
 * 与全量同步相比，增量同步：
 * <ul>
 *   <li>仅 upsert 数据到 ES，不做孤儿清理（不删除 ES 中多余的文档）；</li>
 *   <li>图库段仅 upsert 节点，不删除孤儿节点；</li>
 *   <li>可安全地与全量同步、CDC 增量消费交错执行；</li>
 *   <li>并发保护：AtomicBoolean 互斥，同一实例同时只允许一个增量同步在跑。</li>
 * </ul>
 */
@Slf4j
@Service
public class CdcIncrementalSyncServiceImpl implements CdcIncrementalSyncService {

    private static final int PAGE_SIZE = 1000;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    private OntologySpaceMapper spaceMapper;
    @Autowired
    private OntologyMetaMapper metaMapper;
    @Autowired
    private OntologyPropertyMapper propertyMapper;
    @Autowired
    private OntologyLinkGroupMapper linkGroupMapper;
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
    private OntologyLinkGroupRepository linkGroupRepository;
    @Autowired
    private OntologyInstanceRepository instanceRepository;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private EntityService entityService;

    @Value("${foundry.integration.arangodb.enabled:true}")
    private boolean graphEnabled;

    @Override
    public CdcFullSyncResultVO incrementalSync(CdcFullSyncParam param) {
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException("增量同步正在进行中，请稍后再试",
                    ResultCode.DUPLICATION, HttpStatus.CONFLICT);
        }
        try {
            CdcFullSyncParam options = param == null ? new CdcFullSyncParam() : param;
            long start = System.currentTimeMillis();
            CdcFullSyncResultVO.CdcFullSyncResultVOBuilder result = CdcFullSyncResultVO.builder();

            List<Integer> spaceIds = options.getSpaceIds();
            boolean filterBySpace = !CollectionUtils.isEmpty(spaceIds);

            if (!Boolean.FALSE.equals(options.getSyncSpace())) {
                long synced = filterBySpace
                        ? syncIndexUpsertOnlyWithFilter(spaceMapper, null, OntologySpace::getId,
                                OntologySpaceConverter::convert, spaceRepository,
                                wrapper -> wrapper.in(OntologySpace::getId, spaceIds), null)
                        : syncIndexUpsertOnly(spaceMapper, null, OntologySpace::getId,
                                OntologySpaceConverter::convert, spaceRepository);
                result.spaceSynced(synced).spaceDeleted(0);
            }

            Map<Integer, OntologySpace> spaceById = new HashMap<>();
            Map<String, OntologyMeta> metaByUid = new HashMap<>();

            if (!Boolean.FALSE.equals(options.getSyncMeta())) {
                LambdaQueryWrapper<OntologyMeta> metaWrapper = new LambdaQueryWrapper<OntologyMeta>()
                        .ne(OntologyMeta::getStatus, Status.DELETE.getValue());
                if (filterBySpace) {
                    metaWrapper.in(OntologyMeta::getOntologySpaceId, spaceIds);
                }
                List<OntologyMeta> allMetas = metaMapper.selectList(metaWrapper);
                Set<Integer> metaSpaceIds = allMetas.stream()
                        .map(OntologyMeta::getOntologySpaceId).filter(Objects::nonNull).collect(Collectors.toSet());
                if (!metaSpaceIds.isEmpty()) {
                    spaceMapper.selectBatchIds(metaSpaceIds).forEach(s -> spaceById.put(s.getId(), s));
                }
                allMetas.stream().filter(m -> StringUtils.isNotEmpty(m.getUniqueIdentifier()))
                        .forEach(m -> metaByUid.put(m.getUniqueIdentifier(), m));

                List<EsOntologyMetaDTO> metaDocs = allMetas.stream()
                        .map(OntologyMetaConverter::convert).filter(Objects::nonNull).collect(Collectors.toList());
                for (EsOntologyMetaDTO doc : metaDocs) {
                    OntologySpace space = doc.getOntologySpaceId() != null ? spaceById.get(doc.getOntologySpaceId().intValue()) : null;
                    doc.setSpaceName(space != null ? space.getDisplayName() : null);
                    doc.setSpaceType(space != null ? space.getType() : null);
                }
                long synced = saveUpsertOnly(metaDocs, metaRepository);
                result.metaSynced(synced).metaDeleted(0);
            }

            if (!Boolean.FALSE.equals(options.getSyncProperty())) {
                if (metaByUid.isEmpty()) {
                    LambdaQueryWrapper<OntologyMeta> metaWrapper = new LambdaQueryWrapper<OntologyMeta>()
                            .ne(OntologyMeta::getStatus, Status.DELETE.getValue());
                    if (filterBySpace) {
                        metaWrapper.in(OntologyMeta::getOntologySpaceId, spaceIds);
                    }
                    List<OntologyMeta> allMetas = metaMapper.selectList(metaWrapper);
                    Set<Integer> propSpaceIds = new java.util.HashSet<>();
                    allMetas.stream().map(OntologyMeta::getOntologySpaceId).filter(Objects::nonNull).forEach(propSpaceIds::add);
                    if (!propSpaceIds.isEmpty()) {
                        spaceMapper.selectBatchIds(propSpaceIds).forEach(s -> spaceById.put(s.getId(), s));
                    }
                    allMetas.stream().filter(m -> StringUtils.isNotEmpty(m.getUniqueIdentifier()))
                            .forEach(m -> metaByUid.put(m.getUniqueIdentifier(), m));
                }
                Set<String> allowedMetaUids = filterBySpace ? metaByUid.keySet() : null;
                long synced = syncIndexUpsertOnlyWithFilter(propertyMapper, OntologyProperty::getStatus, OntologyProperty::getId,
                        OntologyPropertyConverter::convert, propertyRepository,
                        wrapper -> {
                            if (filterBySpace) {
                                wrapper.in(OntologyProperty::getOntologyUniqueIdentifier, allowedMetaUids);
                            }
                        },
                        doc -> {
                            OntologyMeta meta = metaByUid.get(doc.getOntologyUniqueIdentifier());
                            if (meta != null) {
                                doc.setOntologyName(meta.getDisplayName());
                                doc.setOntologySpaceId(meta.getOntologySpaceId());
                                OntologySpace space = meta.getOntologySpaceId() != null ? spaceById.get(meta.getOntologySpaceId()) : null;
                                doc.setSpaceName(space != null ? space.getDisplayName() : null);
                                doc.setSpaceType(space != null ? space.getType() : null);
                            }
                        });
                result.propertySynced(synced).propertyDeleted(0);
            }

            if (!Boolean.FALSE.equals(options.getSyncLinkGroup())) {
                if (spaceById.isEmpty() && !filterBySpace) {
                    List<OntologyLinkGroup> allLinks = linkGroupMapper.selectList(new LambdaQueryWrapper<OntologyLinkGroup>()
                            .ne(OntologyLinkGroup::getStatus, Status.DELETE.getValue()));
                    Set<Integer> linkSpaceIds = allLinks.stream()
                            .map(OntologyLinkGroup::getOntologySpaceId).filter(Objects::nonNull).collect(Collectors.toSet());
                    if (!linkSpaceIds.isEmpty()) {
                        spaceMapper.selectBatchIds(linkSpaceIds).forEach(s -> spaceById.put(s.getId(), s));
                    }
                } else if (filterBySpace && spaceById.isEmpty()) {
                    spaceMapper.selectBatchIds(spaceIds).forEach(s -> spaceById.put(s.getId(), s));
                }
                long synced = syncIndexUpsertOnlyWithFilter(linkGroupMapper, OntologyLinkGroup::getStatus, OntologyLinkGroup::getId,
                        OntologyLinkGroupConverter::convert, linkGroupRepository,
                        wrapper -> {
                            if (filterBySpace) {
                                wrapper.in(OntologyLinkGroup::getOntologySpaceId, spaceIds);
                            }
                        },
                        doc -> {
                            OntologySpace space = doc.getOntologySpaceId() != null ? spaceById.get(doc.getOntologySpaceId().intValue()) : null;
                            doc.setSpaceName(space != null ? space.getDisplayName() : null);
                            doc.setSpaceType(space != null ? space.getType() : null);
                        });
                result.linkGroupSynced(synced).linkGroupDeleted(0);
            }

            if (!Boolean.FALSE.equals(options.getSyncInstance())) {
                boolean syncGraph = graphEnabled && !Boolean.FALSE.equals(options.getSyncGraph());
                long[] r = syncInstanceIndexUpsertOnly(options.getOntologyUids(), spaceIds, syncGraph);
                result.instanceSynced(r[0]).instanceDeleted(0)
                        .graphNodeSynced(r[1]).graphNodeDeleted(0);
            }

            refreshAllIndices();
            CdcFullSyncResultVO vo = result.costMillis(System.currentTimeMillis() - start).build();
            log.info("CDC 增量同步完成: {}", vo);
            return vo;
        } finally {
            running.set(false);
        }
    }

    // ------------------------------------------------------------------
    // 主库元数据索引：space / meta / property / link_group（仅 upsert，不清理孤儿）
    // ------------------------------------------------------------------

    private <P, D, I> long syncIndexUpsertOnlyWithFilter(BaseMapper<P> mapper,
                                                           SFunction<P, ?> statusCol,
                                                           SFunction<P, ?> idCol,
                                                           Function<P, D> converter,
                                                           ElasticsearchRepository<D, I> repository,
                                                           Consumer<LambdaQueryWrapper<P>> wrapperCustomizer,
                                                           Consumer<D> enricher) {
        LambdaQueryWrapper<P> wrapper = new LambdaQueryWrapper<>();
        if (statusCol != null) {
            wrapper.ne(statusCol, Status.DELETE.getValue());
        }
        if (wrapperCustomizer != null) {
            wrapperCustomizer.accept(wrapper);
        }
        wrapper.orderByAsc(idCol);
        List<P> rows = mapper.selectList(wrapper);
        List<D> docs = rows.stream().map(converter).filter(Objects::nonNull).collect(Collectors.toList());
        if (enricher != null) {
            docs.forEach(enricher);
        }
        repository.saveAll(docs);
        log.info("增量同步[{}]: 写入 {} 条", JSONObject.toJSONString(repository.getClass()), docs.size());
        return docs.size();
    }

    private <P, D, I> long syncIndexUpsertOnly(BaseMapper<P> mapper,
                                                 SFunction<P, ?> statusCol,
                                                 SFunction<P, ?> idCol,
                                                 Function<P, D> converter,
                                                 ElasticsearchRepository<D, I> repository,
                                                 Consumer<D> enricher) {
        return syncIndexUpsertOnlyWithFilter(mapper, statusCol, idCol, converter, repository, null, enricher);
    }

    private <P, D, I> long syncIndexUpsertOnly(BaseMapper<P> mapper,
                                                 SFunction<P, ?> statusCol,
                                                 SFunction<P, ?> idCol,
                                                 Function<P, D> converter,
                                                 ElasticsearchRepository<D, I> repository) {
        return syncIndexUpsertOnlyWithFilter(mapper, statusCol, idCol, converter, repository, null, null);
    }

    private <D, I> long saveUpsertOnly(List<D> docs,
                                        ElasticsearchRepository<D, I> repository) {
        repository.saveAll(docs);
        log.info("增量同步[{}]: 写入 {} 条", JSONObject.toJSONString(repository.getClass()), docs.size());
        return docs.size();
    }

    // ------------------------------------------------------------------
    // 数据湖实例索引 ontology_instance（仅 upsert，不清理孤儿）
    // ------------------------------------------------------------------

    /**
     * @param syncGraph 是否同步 ArangoDB 图节点
     * @return [实例写入数, 图节点写入数]
     */
    private long[] syncInstanceIndexUpsertOnly(List<String> ontologyUidFilter, List<Integer> spaceIds, boolean syncGraph) {
        LambdaQueryWrapper<OntologyMeta> metaWrapper = new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue());
        if (!CollectionUtils.isEmpty(spaceIds)) {
            metaWrapper.in(OntologyMeta::getOntologySpaceId, spaceIds);
        }
        Map<String, OntologyMeta> enabledMetas = metaMapper.selectList(metaWrapper)
                .stream()
                .filter(m -> StringUtils.isNotEmpty(m.getUniqueIdentifier()))
                .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, m -> m, (a, b) -> a, LinkedHashMap::new));

        if (!CollectionUtils.isEmpty(ontologyUidFilter)) {
            enabledMetas.keySet().retainAll(new java.util.HashSet<>(ontologyUidFilter));
        }

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
        long nodeSynced = 0;
        for (OntologyMeta meta : enabledMetas.values()) {
            OntologySpace space = meta.getOntologySpaceId() == null
                    ? null : spaceById.get(meta.getOntologySpaceId());
            String schema = space == null ? null : space.getApiName();
            if (StringUtils.isEmpty(schema)) {
                log.warn("本体[{}]未关联空间或缺少 apiName，跳过其实例增量同步", meta.getUniqueIdentifier());
                continue;
            }
            long[] tableResult = syncInstancesForOntologyUpsertOnly(meta, space, schema, syncGraph);
            synced += tableResult[0];
            nodeSynced += tableResult[1];
        }
        return new long[]{synced, nodeSynced};
    }

    /**
     * 单个本体维度：找出其全部主表（主键属性数据源表），分页读取行数据 upsert 实例文档。
     * 不做孤儿清理。
     *
     * @return [实例写入数, 图节点写入数]
     */
    private long[] syncInstancesForOntologyUpsertOnly(OntologyMeta meta, OntologySpace space, String schema, boolean syncGraph) {
        List<OntologyProperty> pkProps = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getOntologyUniqueIdentifier, meta.getUniqueIdentifier())
                .eq(OntologyProperty::getIsPrimaryKey, 1)
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()));
        Map<String, List<OntologyProperty>> propsByTable = pkProps.stream()
                .filter(p -> StringUtils.isNotEmpty(p.getDatasourceId()))
                .collect(Collectors.groupingBy(OntologyProperty::getDatasourceId, LinkedHashMap::new,
                        Collectors.toList()));

        long synced = 0;
        long nodeSynced = 0;
        for (Map.Entry<String, List<OntologyProperty>> entry : propsByTable.entrySet()) {
            String table = entry.getKey();
            if (!Boolean.TRUE.equals(tableMetadataMapper.isTableExist(schema, table))) {
                log.warn("本体[{}]主表[{}.{}]不存在，跳过", meta.getUniqueIdentifier(), schema, table);
                continue;
            }
            OntologyProperty titleProp = propertyMapper.selectList(new LambdaQueryWrapper<OntologyProperty>()
                            .eq(OntologyProperty::getOntologyUniqueIdentifier, meta.getUniqueIdentifier())
                            .eq(OntologyProperty::getIsTitleKey, 1)
                            .eq(OntologyProperty::getDatasourceId, table)
                            .eq(OntologyProperty::getStatus, Status.ENABLE.getValue()))
                    .stream().findFirst().orElse(null);
            String pkColumn = entry.getValue().get(0).getDatasourceColumnName();
            Map<Object, String> pkToDisplayName = new LinkedHashMap<>();
            synced += pageAndUpsertRowsUpsertOnly(meta, space, schema, table, pkColumn, titleProp, pkToDisplayName);
            if (syncGraph && !pkToDisplayName.isEmpty()) {
                try {
                    nodeSynced += entityService.upsertEntityNodes(meta.getUniqueIdentifier(), table, pkToDisplayName);
                } catch (Exception e) {
                    log.error("增量同步图节点失败, ontology={}, table=[{}.{}]",
                            meta.getUniqueIdentifier(), schema, table, e);
                }
            }
        }
        return new long[]{synced, nodeSynced};
    }

    /**
     * 分页读取数据湖表并逐批 upsert 实例文档（不做孤儿清理）。
     *
     * @param pkToDisplayName 出参，收集「主键值 -> 展示名」供调用方批量 upsert 图节点
     * @return 写入文档数
     */
    private long pageAndUpsertRowsUpsertOnly(OntologyMeta meta, OntologySpace space, String schema, String table,
                                              String pkColumn, OntologyProperty titleProp,
                                              Map<Object, String> pkToDisplayName) {
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
                pkToDisplayName.put(pkValue, dto.getName());
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
        log.info("数据湖[{}.{}]本体[{}]实例增量写入 {} 条", schema, table, meta.getUniqueIdentifier(), synced);
        return synced;
    }

    private void refreshAllIndices() {
        refreshIndexIfPresent(EsOntologySpaceDTO.class);
        refreshIndexIfPresent(EsOntologyMetaDTO.class);
        refreshIndexIfPresent(EsOntologyPropertyDTO.class);
        refreshIndexIfPresent(EsOntologyLinkGroupDTO.class);
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
