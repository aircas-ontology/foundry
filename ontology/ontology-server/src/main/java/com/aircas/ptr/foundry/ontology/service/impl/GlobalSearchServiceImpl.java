package com.aircas.ptr.foundry.ontology.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.param.GlobalSearchParam;
import com.aircas.ptr.foundry.ontology.model.vo.GlobalSearchHitVO;
import com.aircas.ptr.foundry.ontology.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 全局检索：一次查询同时命中五个本体索引，query_string 匹配所有字段（fields=*），
 * 结果按相关性得分（_score）降序混排，返回名称/类型/描述/空间 id/对象 id/实例 id/属性 id。
 * <p>
 * 属性与实例文档只存所属对象的 unique_identifier（ontology_unique_identifier / ontology_uid），
 * 需批量反查 ontology_meta 换取对象 id 与空间 id。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSearchServiceImpl implements GlobalSearchService {

    /** 参与全局检索的五个索引 */
    private static final List<String> SEARCH_INDICES = List.of(
            "ontology_space", "ontology_meta", "ontology_property", "ontology_instance", "ontology_link_group");

    /** 命中项 type 取值 */
    private static final String TYPE_SPACE = "空间";
    private static final String TYPE_META = "对象";
    private static final String TYPE_PROPERTY = "属性";
    private static final String TYPE_INSTANCE = "实例";
    private static final String TYPE_LINK_GROUP = "关系分组";

    /** 缺省返回条数 */
    private static final int DEFAULT_SIZE = 100;

    /** 返回条数上限 */
    private static final int MAX_SIZE = 1000;

    /** 只需取回映射 VO 用到的字段，避免实例文档整行数据回传 */
    private static final List<String> SOURCE_FIELDS = List.of(
            "id", "display_name", "description", "name", "search_text",
            "ontology_space_id", "space_id", "pk", "ontology_uid", "ontology_unique_identifier");

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public List<GlobalSearchHitVO> search(GlobalSearchParam param) {
        int size = param.getSize() == null || param.getSize() <= 0 ? DEFAULT_SIZE
                : Math.min(param.getSize(), MAX_SIZE);
        SearchRequest request = SearchRequest.of(b -> b
                .index(SEARCH_INDICES)
                .size(size)
                // 只取回组装 VO 需要的字段
                .source(includeSource(SOURCE_FIELDS))
                .query(q -> q.queryString(qs -> qs
                        // fields=* 匹配所有字段；lenient 忽略 date/number 等字段类型转换失败
                        .fields("*")
                        .lenient(true)
                        .defaultOperator(Operator.Or)
                        .query(escapeQuery(param.getKeyword())))));
        List<Hit<Map>> hits;
        try {
            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
            hits = response.hits().hits();
        } catch (IOException e) {
            log.error("全局检索失败, keyword={}", param.getKeyword(), e);
            throw new BusinessException("全局检索失败：" + e.getMessage(), ResultCode.ERROR);
        }

        // 属性/实例文档仅存对象 unique_identifier，批量反查 meta 索引换取对象 id / 空间 id
        Set<String> uidNeeded = hits.stream()
                .filter(h -> h.source() != null)
                .filter(h -> "ontology_property".equals(h.index()) || "ontology_instance".equals(h.index()))
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

    /** 属性/实例文档上指向所属对象的 unique_identifier */
    private static String uidOf(String index, Map<String, Object> src) {
        Object uid = "ontology_property".equals(index)
                ? src.get("ontology_unique_identifier") : src.get("ontology_uid");
        return uid == null ? null : uid.toString();
    }

    private static GlobalSearchHitVO toVO(String index, String hitId, Map<String, Object> src,
                                          Map<String, MetaRef> metaByUid) {
        GlobalSearchHitVO.GlobalSearchHitVOBuilder vo = GlobalSearchHitVO.builder();
        // 非实例索引 _id 即数据库主键，_source 里 id 缺失时回退用 _id
        Long docId = longOf(src.get("id")) != null ? longOf(src.get("id")) : longOf(hitId);
        switch (index) {
            case "ontology_space" -> vo.name(str(src.get("display_name")))
                    .type(TYPE_SPACE)
                    .desc(str(src.get("description")))
                    .spaceId(docId);
            case "ontology_meta" -> vo.name(str(src.get("display_name")))
                    .type(TYPE_META)
                    .desc(str(src.get("description")))
                    .spaceId(longOf(src.get("ontology_space_id")))
                    .objectId(docId);
            case "ontology_property" -> {
                MetaRef ref = metaByUid.get(str(src.get("ontology_unique_identifier")));
                vo.name(str(src.get("display_name")))
                        .type(TYPE_PROPERTY)
                        .desc(str(src.get("description")))
                        .spaceId(ref == null ? null : ref.spaceId())
                        .objectId(ref == null ? null : ref.metaId())
                        .propertyId(docId);
            }
            case "ontology_instance" -> {
                MetaRef ref = metaByUid.get(str(src.get("ontology_uid")));
                vo.name(str(src.get("name")))
                        .type(TYPE_INSTANCE)
                        .desc(str(src.get("search_text")))
                        .spaceId(longOf(src.get("space_id")))
                        .objectId(ref == null ? null : ref.metaId())
                        .instanceId(hitId);
            }
            case "ontology_link_group" -> vo.name(str(src.get("name")))
                    .type(TYPE_LINK_GROUP)
                    .spaceId(longOf(src.get("ontology_space_id")));
            default -> {
                return null;
            }
        }
        return vo.build();
    }

    /** meta 反查结果：对象（ontology_meta）主键与所属空间 id */
    private record MetaRef(Long metaId, Long spaceId) {
    }

    /** 按 unique_identifier 批量反查 ontology_meta，取 id 与 ontology_space_id */
    private Map<String, MetaRef> findMetaByUid(Set<String> uids) {
        List<FieldValue> values = uids.stream().map(FieldValue::of).toList();
        SearchRequest request = SearchRequest.of(b -> b
                .index("ontology_meta")
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
            // 反查失败不影响主流程，仅属性/实例命中的 objectId/spaceId 缺省为 null
            log.error("全局检索反查对象失败, uids={}", uids, e);
            return Map.of();
        }
    }

    /**
     * 构建只取回指定字段的 _source 配置。
     * 注意：elasticsearch-java 8.18 的 SourceConfig.Builder setter 返回 ObjectBuilder 接口，无法链式，需逐条语句设置。
     */
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

    /**
     * 转义 query_string 保留字符，避免用户输入中的 : / && 等被当作查询语法解析。
     */
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
