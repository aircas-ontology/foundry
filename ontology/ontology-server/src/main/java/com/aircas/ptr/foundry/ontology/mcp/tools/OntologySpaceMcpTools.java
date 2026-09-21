package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本体空间 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} 声明本体空间的只读原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。方法委托既有 Service，
 * 直接返回自有业务 VO，Spring AI 自动序列化为 MCP 结果。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologySpaceMcpTools implements McpTools {

    private final OntologySpaceService ontologySpaceService;

    @Tool(description = """
            查询平台上所有本体空间（Ontology Space）列表。
            当用户需要选择本体空间、查看有哪些可用空间，或询问"有哪些空间""列一下空间""选个空间"，
            或在新建本体流程起点（第0步）尚未确定 spaceId 时使用。
            返回每个空间的 spaceId、名称（displayName）、apiName、描述及本体/属性/关系数量统计；
            其中 spaceId 是后续所有需要空间 id 的操作（查询数据源、新建本体对象、最终落库等）的必填上下文。
            无可用空间时返回空集合。
            """)
    public List<OntologySpaceVO> listOntologySpaces() {
        return ontologySpaceService.querySpace();
    }
}
