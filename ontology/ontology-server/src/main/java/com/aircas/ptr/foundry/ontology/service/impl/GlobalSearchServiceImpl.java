package com.aircas.ptr.foundry.ontology.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.param.GlobalSearchParam;
import com.aircas.ptr.foundry.ontology.model.vo.GlobalSearchHitVO;
import com.aircas.ptr.foundry.ontology.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局检索：一次查询同时命中五个本体索引，query_string 匹配所有字段（fields=*），
 * 结果按相关性得分（_score）降序混排，返回命中文档 id 列表。
 * <p>
 * 各索引 _id 即业务主键：ontology_space/meta/property/link_group 为数据库主键 id，
 * ontology_instance 为 pk（{schema}.{table}.{ontology_uid}.{主键值}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSearchServiceImpl implements GlobalSearchService {

    /** 参与全局检索的五个索引 */
    private static final List<String> SEARCH_INDICES = List.of(
            "ontology_space", "ontology_meta", "ontology_property", "ontology_instance", "ontology_link_group");

    /** 缺省返回条数 */
    private static final int DEFAULT_SIZE = 100;

    /** 返回条数上限 */
    private static final int MAX_SIZE = 1000;

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public List<GlobalSearchHitVO> search(GlobalSearchParam param) {
        int size = param.getSize() == null || param.getSize() <= 0 ? DEFAULT_SIZE
                : Math.min(param.getSize(), MAX_SIZE);
        SearchRequest request = SearchRequest.of(b -> b
                .index(SEARCH_INDICES)
                .size(size)
                // 不取回 _source，只需要 id/索引名/得分
                .source(s -> s.fetch(false))
                .query(q -> q.queryString(qs -> qs
                        // fields=* 匹配所有字段；lenient 忽略 date/number 等字段类型转换失败
                        .fields("*")
                        .lenient(true)
                        .defaultOperator(Operator.Or)
                        .query(escapeQuery(param.getKeyword())))));
        try {
            SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);
            return response.hits().hits().stream()
                    .map(hit -> GlobalSearchHitVO.builder()
                            .id(hit.id())
                            .index(hit.index())
                            .score(hit.score())
                            .build())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("全局检索失败, keyword={}", param.getKeyword(), e);
            throw new BusinessException("全局检索失败：" + e.getMessage(), ResultCode.ERROR);
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
