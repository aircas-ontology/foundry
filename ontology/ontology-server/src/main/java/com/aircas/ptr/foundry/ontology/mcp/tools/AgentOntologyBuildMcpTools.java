package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.param.AgentOntologyBuildParam;
import com.aircas.ptr.foundry.ontology.model.vo.AgentOntologyBuildResultVO;
import com.aircas.ptr.foundry.ontology.service.AgentOntologyBuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Agent 本体构建落库 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明「最终落库」写操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集为 {@code ToolCallbackProvider}，
 * agent-server 端经 {@code tools/list} 动态发现并按需调用。方法委托 {@link AgentOntologyBuildService}，
 * 在单一事务内完成本体元数据 + 属性 + 关系的级联落库，编排方式与画布一键建空间一致。</p>
 *
 * <p>这是当前 MCP 工具集中**唯一的写操作**，其余均为只读查询；调用前 Agent 须已完成对象定义、
 * 属性选择与关系选择，并经用户确认。</p>
 */
@Component
@RequiredArgsConstructor
public class AgentOntologyBuildMcpTools implements McpTools {

    private final AgentOntologyBuildService agentOntologyBuildService;

    @Tool(description = """
            将 Agent 构建的本体对象一次性落库：在指定空间下创建本体元数据，并级联写入用户已勾选的属性与关系。
            仅在用户明确确认"保存/提交/落库/确认创建"后调用，且必须已具备对象定义、属性选择、关系选择三部分汇总结果。
            整个落库在单一事务内完成，任一环节失败整体回滚，不会产生半成品数据。
            入参为结构化对象：spaceId（必填）、categoryId（本体分类，可空）、displayName/apiName/description（对象定义）、
            properties（属性列表，每条含 displayName/apiName/dataType/description/isPrimaryKey）、
            relations（关系列表，每条含 name/targetUniqueIdentifier/type，type 取 COMPOSITION/POSSESSION/ATTRIBUTION）。
            返回新本体的 uniqueIdentifier 及属性、关系落库数量统计。
            """)
    public AgentOntologyBuildResultVO persistOntologyBuild(
            @ToolParam(description = "本体构建落库入参（对象定义 + 属性列表 + 关系列表）") AgentOntologyBuildParam param) {
        return agentOntologyBuildService.buildOntology(param);
    }
}
