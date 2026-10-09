package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本体分组 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明本体分组的只读原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。方法委托既有 Service，
 * 直接返回自有业务 VO，Spring AI 自动序列化为 MCP 结果。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyGroupMcpTools implements McpTools {

    private final OntologyGroupService ontologyGroupService;

    @Tool(description = """
            根据本体空间 id（spaceId）查询该空间下的所有本体分组。
            当用户想了解某个空间内有哪些分组、分组如何组织时使用。
            返回分组 id、名称、描述及所属空间 id。
            """)
    public List<OntologyGroupInfoVO> listGroupsBySpace(
            @ToolParam(description = "本体空间 id（spaceId）") Integer spaceId) {
        return ontologyGroupService.getGroupBySpaceId(spaceId);
    }
}
