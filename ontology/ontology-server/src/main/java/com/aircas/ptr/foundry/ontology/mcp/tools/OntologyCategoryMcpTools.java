package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyCategoryVO;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本体分类 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明本体分类的只读原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。方法委托既有 Service，
 * 直接返回自有业务 VO，Spring AI 自动序列化为 MCP 结果。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyCategoryMcpTools implements McpTools {

    private final OntologyCategoryService ontologyCategoryService;

    @Tool(description = """
            根据本体空间 id（spaceId）查询该空间下的分类列表（拍平）。
            当用户需要为本体对象选择分类时使用。
            返回分类 id 和名称列表。
            """)
    public List<OntologyCategoryVO> listCategoriesBySpace(
            @ToolParam(description = "本体空间 id（spaceId）") Integer spaceId) {
        // Service 返回分类树，此处拍平为仅含 categoryId / name 的扁平列表，便于大模型选择分类
        OntologyCategoryVO tree = ontologyCategoryService.getCategoryTree(spaceId);
        List<OntologyCategoryVO> flat = new ArrayList<>();
        flatten(tree, flat);
        return flat;
    }

    /**
     * 递归拍平分类树，仅保留 {@code categoryId} / {@code name}（其余字段留空，序列化时按 non_null 省略）。
     */
    private void flatten(OntologyCategoryVO node, List<OntologyCategoryVO> result) {
        if (node == null) {
            return;
        }
        result.add(OntologyCategoryVO.builder()
                .categoryId(node.getCategoryId())
                .name(node.getName())
                .build());
        if (node.getChildren() != null) {
            for (OntologyCategoryVO child : node.getChildren()) {
                flatten(child, result);
            }
        }
    }
}
