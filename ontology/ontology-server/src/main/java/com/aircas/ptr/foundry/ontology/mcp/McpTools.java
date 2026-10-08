package com.aircas.ptr.foundry.ontology.mcp;

/**
 * MCP 工具类标记接口。
 *
 * <p>所有承载 {@code @Tool} 方法的工具 Bean 都实现本接口；{@code McpServerConfig} 通过注入
 * {@code List<McpTools>} 自动收集容器内全部工具类，构造 {@code ToolCallbackProvider}。</p>
 *
 * <p>引入动机：随着业务增长，工具类会按实体越拆越多（本体元数据 / 分组 / 分类 / 数据源 / ...），
 * 若每加一个类都要改 {@code McpServerConfig} 的注入列表，维护成本线性上升。用标记接口 + Spring
 * 集合注入后，新增工具类只需 {@code implements McpTools}，无需触碰配置。</p>
 *
 * <p>按实体拆分约定：一个 {@code XxxMcpTools} 类聚焦一个业务实体（本体元数据、本体分组、数据源等），
 * 类名后缀统一为 {@code McpTools}，实现类放在子包 {@code mcp.tools} 下；本标记接口位于父包 {@code mcp}，
 * 便于配置类 {@code McpServerConfig} 就近引用。</p>
 */
public interface McpTools {
}
