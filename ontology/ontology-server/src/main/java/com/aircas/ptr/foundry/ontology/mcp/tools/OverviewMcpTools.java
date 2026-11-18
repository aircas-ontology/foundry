package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.vo.OverviewCountVO;
import com.aircas.ptr.foundry.ontology.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 概览统计 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明概览页统计原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。方法委托既有 Service，
 * 直接返回自有业务 VO，Spring AI 自动序列化为 MCP 结果。</p>
 */
@Component
@RequiredArgsConstructor
public class OverviewMcpTools implements McpTools {

    private final OverviewService overviewService;

    @Tool(description = """
            获取平台概览页统计数据（只读）：空间、对象、关系、属性、行为、函数等的总量。
            当用户询问"平台里有多少对象/关系/空间/函数""整体规模如何"时使用。
            参数 type 为空间类型：0=空间（默认），1=主题/子空间；仅影响 spaceCount 的统计口径，其余计数不受影响。
            返回 {spaceCount（空间数）, ontologyCount（对象数）, groupCount（关系分组数）, linkCount（关系数）,
            propertyCount（属性数）, actionCount（行为数）, actionSchedulingCount（调度中行为数）, functionCount（函数数）}。
            计数为 0 表示确实没有该类数据，不是错误。
            """)
    public OverviewCountVO getOverviewCount(
            @ToolParam(description = "空间类型：0=空间（默认），1=主题/子空间；不传按 0 统计", required = false) Integer type) {
        return overviewService.getCount(type == null ? 0 : type);
    }
}