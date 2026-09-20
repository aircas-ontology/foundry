package com.aircas.ptr.foundry.agent.server.tool;

import com.aircas.ptr.foundry.agent.tools.api.OntologyQueryTools;
import com.aircas.ptr.foundry.agent.tools.api.model.OntologyGroupToolVO;
import com.aircas.ptr.foundry.agent.tools.api.model.OntologyMetaToolVO;
import com.aircas.ptr.foundry.common.base.RestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * 本体查询工具的 HTTP 适配器。
 *
 * <p>混合模式消费端：实现 {@link OntologyQueryTools} 契约，通过 {@link RestClient} 调用
 * ontology-server 暴露的原子操作（{@code /tool/**}），并在方法上标注 Spring AI
 * {@link Tool} / {@link ToolParam}，使其可被注册到 ChatClient 供大模型按需调用。</p>
 *
 * <p>工具描述（{@code @Tool#description}）是大模型判断"是否调用该工具"的关键依据，
 * 需清晰说明用途、触发场景与返回内容。</p>
 */
@Slf4j
@Component
public class HttpOntologyQueryTools implements OntologyQueryTools {

    private final RestClient ontologyRestClient;

    public HttpOntologyQueryTools(RestClient ontologyRestClient) {
        this.ontologyRestClient = ontologyRestClient;
    }

    @Override
    @Tool(description = """
            按关键词搜索本体（Ontology）元数据。
            当用户想查找、检索、列出本体，或询问"有哪些本体""某某本体是否存在"时使用。
            返回本体的名称、描述、唯一标识、所属空间及实例/关系/属性数量等信息。
            关键词为空时返回全部本体。
            """)
    public List<OntologyMetaToolVO> searchOntologyMeta(
            @ToolParam(description = "搜索关键词，可为空；为空时返回全部本体", required = false) String keyword) {
        RestResult<List<OntologyMetaToolVO>> result = ontologyRestClient.get()
                .uri(builder -> builder.path("/tool/meta_search")
                        .queryParamIfPresent("keyword", java.util.Optional.ofNullable(keyword))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<RestResult<List<OntologyMetaToolVO>>>() {
                });
        List<OntologyMetaToolVO> data = unwrap(result);
        return data != null ? data : Collections.emptyList();
    }

    @Override
    @Tool(description = """
            根据本体唯一标识（uniqueIdentifier）查询单个本体的元数据详情。
            当用户已经知道某个本体的唯一标识，想了解该本体的详细信息时使用。
            返回该本体的名称、描述、所属空间及各类数量统计。
            """)
    public OntologyMetaToolVO getOntologyMetaDetail(
            @ToolParam(description = "本体唯一标识 uniqueIdentifier") String uniqueIdentifier) {
        RestResult<OntologyMetaToolVO> result = ontologyRestClient.get()
                .uri(builder -> builder.path("/tool/meta_detail")
                        .queryParam("uniqueIdentifier", uniqueIdentifier)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<RestResult<OntologyMetaToolVO>>() {
                });
        return unwrap(result);
    }

    @Override
    @Tool(description = """
            根据本体空间 id（spaceId）查询该空间下的所有本体分组。
            当用户想了解某个空间内有哪些分组、分组如何组织时使用。
            返回分组 id、名称、描述及所属空间 id。
            """)
    public List<OntologyGroupToolVO> listGroupsBySpace(
            @ToolParam(description = "本体空间 id（spaceId）") Integer spaceId) {
        RestResult<List<OntologyGroupToolVO>> result = ontologyRestClient.get()
                .uri(builder -> builder.path("/tool/group_by_space")
                        .queryParam("spaceId", spaceId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<RestResult<List<OntologyGroupToolVO>>>() {
                });
        List<OntologyGroupToolVO> data = unwrap(result);
        return data != null ? data : Collections.emptyList();
    }

    /**
     * 解包 {@link RestResult}，失败时记录日志并返回 {@code null}，避免把异常直接抛给大模型工具调用链。
     */
    private <T> T unwrap(RestResult<T> result) {
        if (result == null) {
            log.warn("[AgentTool] ontology-server 返回空响应");
            return null;
        }
        if (result.getCode() == null || result.getCode() != 200) {
            log.warn("[AgentTool] ontology-server 返回非成功状态: code={}, message={}",
                    result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
