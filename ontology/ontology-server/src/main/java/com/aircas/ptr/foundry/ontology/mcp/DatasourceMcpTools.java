package com.aircas.ptr.foundry.ontology.mcp;

import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableCommentVO;
import com.aircas.ptr.foundry.ontology.service.DatasourceConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据源查询 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明数据源只读原子操作，
 * 由 {@code McpServerConfig} 注册进 MCP 协议供 agent-server 动态发现。方法委托既有 Service，
 * 直接返回自有业务 VO（不含密码等敏感字段），Spring AI 自动序列化为 MCP 结果。</p>
 */
@Component
@RequiredArgsConstructor
public class DatasourceMcpTools {

    private final DatasourceConnectionService datasourceConnectionService;

    @Tool(description = """
            按关键词查询平台已配置的数据源（数据库连接）列表。
            当用户需要选择数据源、查看有哪些可用数据源，或询问"有哪些数据库""某某数据源是否存在"时使用。
            返回数据源的名称、类型、主机、端口、数据库名、Schema 及描述等信息（不含密码等敏感字段）。
            关键词为空时返回全部启用中的数据源。
            """)
    public List<DatasourceConnectionVO> searchDatasources(
            @ToolParam(description = "搜索关键词，可为空；按数据源名称或数据库类型模糊匹配，为空时返回全部启用数据源", required = false) String keyword) {
        return datasourceConnectionService.searchByKeyword(keyword);
    }

    @Tool(description = """
            扫描指定数据源的表注释（表名 + 表注释）。
            当用户需要基于数据源创建本体对象、匹配表结构时使用。
            返回该数据源下所有表的表名和表注释列表。
            仅支持 PostgreSQL 数据源。
            """)
    public List<TableCommentVO> scanTableComments(
            @ToolParam(description = "数据源 id（datasource_connection 表主键）") Integer datasourceId) {
        return datasourceConnectionService.listTableComments(datasourceId);
    }
}
