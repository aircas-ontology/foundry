package com.aircas.ptr.foundry.ontology.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyInstanceDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyLinkGroupDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyMetaDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologySpaceDTO;
import com.aircas.ptr.foundry.ontology.model.param.GlobalSearchParam;
import com.aircas.ptr.foundry.ontology.model.vo.GlobalSearchHitVO;
import com.aircas.ptr.foundry.ontology.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSearchServiceImpl implements GlobalSearchService {

    private static final String TYPE_SPACE = "空间";
    private static final String TYPE_META = "对象";
    private static final String TYPE_PROPERTY = "属性";
    private static final String TYPE_INSTANCE = "实例";
    private static final String TYPE_LINK_GROUP = "关系分组";

    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 1000;

    private static final List<String> SOURCE_FIELDS = List.of(
            "id", "display_name", "description", "name", "search_text",
            "ontology_space_id", "space_id", "pk", "ontology_uid", "ontology_unique_identifier",
            "unique_identifier", "api_name", "ontology_name", "space_name", "space_display_name");

    /** 参与全局检索的索引列表，从各 ES DTO 的 @Document 注解读取 */
    private static final List<String> SEARCH_INDICES = List.of(
            indexNameOf(EsOntologySpaceDTO.class),
            indexNameOf(EsOntologyMetaDTO.class),
            indexNameOf(EsOntologyPropertyDTO.class),
            indexNameOf(EsOntologyInstanceDTO.class),
            indexNameOf(EsOntologyLinkGroupDTO.class));

    /** 索引名 → 命中类型常量，用于 toVO 分支 */
    private static final Map<String, String> INDEX_TYPE_MAP = Map.of(
            indexNameOf(EsOntologySpaceDTO.class), TYPE_SPACE,
            indexNameOf(EsOntologyMetaDTO.class), TYPE_META,
            indexNameOf(EsOntologyPropertyDTO.class), TYPE_PROPERTY,
            indexNameOf(EsOntologyInstanceDTO.class), TYPE_INSTANCE,
            indexNameOf(EsOntologyLinkGroupDTO.class), TYPE_LINK_GROUP);

    /** 每个索引参与关键词匹配的字段列表 */
    private static final Map<String, List<String>> INDEX_SEARCH_FIELDS = Map.of(
            indexNameOf(EsOntologySpaceDTO.class),      List.of("api_name", "display_name"),
            indexNameOf(EsOntologyMetaDTO.class),       List.of("api_name", "display_name"),
            indexNameOf(EsOntologyPropertyDTO.class),   List.of("display_name", "api_name"),
            indexNameOf(EsOntologyInstanceDTO.class),   List.of("name"),
            indexNameOf(EsOntologyLinkGroupDTO.class),  List.of("name"));

    private final ElasticsearchClient elasticsearchClient;

    private static String indexNameOf(Class<?> clazz) {
        return clazz.getAnnotation(Document.class).indexName();
    }

    @Override
    public List<GlobalSearchHitVO> search(GlobalSearchParam param) {
        int size = param.getSize() == null || param.getSize() <= 0 ? DEFAULT_SIZE
                : Math.min(param.getSize(), MAX_SIZE);
        String escapedKeyword = escapeQuery(param.getKeyword());
        SearchRequest request = SearchRequest.of(b -> b
                .index(SEARCH_INDICES)
                .size(size)
                .source(includeSource(SOURCE_FIELDS))
                .query(q -> q.bool(bool -> {
                    INDEX_SEARCH_FIELDS.forEach((index, fields) -> bool.should(sh -> sh
                            .bool(inner -> inner
                                    .must(m -> m.term(t -> t.field("_index").value(index)))
                                    .must(m -> m.queryString(qs -> qs
                                            .fields(fields)
                                            .lenient(true)
                                            .defaultOperator(Operator.Or)
                                            .query(escapedKeyword)))
                            )));
                    return bool;
                })));
        List<Hit<Map>> hits;
        try {
            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
            hits = response.hits().hits();
        } catch (IOException e) {
            log.error("全局检索失败, keyword={}", param.getKeyword(), e);
            throw new BusinessException("全局检索失败：" + e.getMessage(), ResultCode.ERROR);
        }

        Set<String> uidNeeded = hits.stream()
                .filter(h -> h.source() != null)
                .filter(h -> isPropertyIndex(h.index()) || isInstanceIndex(h.index()))
                .map(h -> uidOf(h.index(), h.source()))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, MetaRef> metaByUid = uidNeeded.isEmpty() ? Map.of() : findMetaByUid(uidNeeded);

        List<GlobalSearchHitVO> result = new ArrayList<>(hits.size());
        for (Hit<Map> hit : hits) {
            Map<String, Object> src = hit.source();
            if (src == null) {
                continue;
            }
            GlobalSearchHitVO vo = toVO(hit.index(), hit.id(), src, metaByUid);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    private static boolean isPropertyIndex(String index) {
        return index.equals(indexNameOf(EsOntologyPropertyDTO.class));
    }

    private static boolean isInstanceIndex(String index) {
        return index.equals(indexNameOf(EsOntologyInstanceDTO.class));
    }

    private static String uidOf(String index, Map<String, Object> src) {
        Object uid = isPropertyIndex(index)
                ? src.get("ontology_unique_identifier") : src.get("ontology_uid");
        return uid == null ? null : uid.toString();
    }

    private static GlobalSearchHitVO toVO(String index, String hitId, Map<String, Object> src,
                                          Map<String, MetaRef> metaByUid) {
        GlobalSearchHitVO.GlobalSearchHitVOBuilder vo = GlobalSearchHitVO.builder();
        Long docId = longOf(src.get("id")) != null ? longOf(src.get("id")) : longOf(hitId);
        String type = INDEX_TYPE_MAP.get(index);
        if (type == null) {
            return null;
        }
        switch (type) {
            case TYPE_SPACE -> vo.name(str(src.get("display_name")))
                    .type(TYPE_SPACE)
                    .desc(str(src.get("description")))
                    .spaceId(docId)
                    .spaceName(str(src.get("display_name")))
                    .uniqueIdentifier(str(src.get("unique_identifier")) != null
                            ? str(src.get("unique_identifier")) : str(src.get("api_name")));
            case TYPE_META -> vo.name(str(src.get("display_name")))
                    .type(TYPE_META)
                    .desc(str(src.get("description")))
                    .spaceId(longOf(src.get("ontology_space_id")))
                    .spaceName(str(src.get("space_name")))
                    .objectId(docId)
                    .uniqueIdentifier(str(src.get("unique_identifier")));
            case TYPE_PROPERTY -> {
                MetaRef ref = metaByUid.get(str(src.get("ontology_unique_identifier")));
                vo.name(str(src.get("display_name")))
                        .type(TYPE_PROPERTY)
                        .desc(str(src.get("description")))
                        .spaceId(ref == null ? null : ref.spaceId())
                        .objectId(ref == null ? null : ref.metaId())
                        .propertyId(docId)
                        .ontologyName(str(src.get("ontology_name")))
                        .spaceName(str(src.get("space_name")))
                        .uniqueIdentifier(str(src.get("unique_identifier")))
                        .ontologyUniqueIdentifier(str(src.get("ontology_unique_identifier")));
            }
            case TYPE_INSTANCE -> {
                MetaRef ref = metaByUid.get(str(src.get("ontology_uid")));
                vo.name(str(src.get("name")))
                        .type(TYPE_INSTANCE)
                        .desc(str(src.get("search_text")))
                        .spaceId(longOf(src.get("space_id")))
                        .spaceName(str(src.get("space_display_name")))
                        .objectId(ref == null ? null : ref.metaId())
                        .instanceId(hitId)
                        .ontologyName(str(src.get("ontology_name")))
                        .uniqueIdentifier(str(src.get("ontology_uid")));
            }
            case TYPE_LINK_GROUP -> vo.name(str(src.get("name")))
                    .type(TYPE_LINK_GROUP)
                    .spaceId(longOf(src.get("ontology_space_id")))
                    .spaceName(str(src.get("space_name")))
                    .uniqueIdentifier(str(src.get("unique_identifier")));
            default -> {
                return null;
            }
        }
        return vo.build();
    }

    private record MetaRef(Long metaId, Long spaceId) {
    }

    private Map<String, MetaRef> findMetaByUid(Set<String> uids) {
        List<FieldValue> values = uids.stream().map(FieldValue::of).toList();
        SearchRequest request = SearchRequest.of(b -> b
                .index(indexNameOf(EsOntologyMetaDTO.class))
                .size(uids.size())
                .source(includeSource(List.of("id", "unique_identifier", "ontology_space_id")))
                .query(q -> q.terms(t -> t.field("unique_identifier").terms(v -> v.value(values)))));
        try {
            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
            Map<String, MetaRef> map = new HashMap<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> src = hit.source();
                if (src == null || src.get("unique_identifier") == null) {
                    continue;
                }
                map.put(src.get("unique_identifier").toString(),
                        new MetaRef(longOf(src.get("id")), longOf(src.get("ontology_space_id"))));
            }
            return map;
        } catch (IOException e) {
            log.error("全局检索反查对象失败, uids={}", uids, e);
            return Map.of();
        }
    }

    private static SourceConfig includeSource(List<String> fields) {
        SourceConfig.Builder builder = new SourceConfig.Builder();
        builder.fetch(true);
        builder.filter(new SourceFilter.Builder().includes(fields).build());
        return builder.build();
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long longOf(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escapeQuery(String keyword) {
        StringBuilder sb = new StringBuilder(keyword.length() + 8);
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            switch (c) {
                case '+', '-', '=', '&', '|', '>', '<', '!', '(', ')', '{', '}', '[', ']',
                     '^', '"', '~', '*', '?', ':', '\\', '/', '\'' -> sb.append('\\');
                default -> {
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
