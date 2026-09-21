package com.aircas.ptr.foundry.ontology.mcp;

import com.aircas.ptr.foundry.ontology.model.vo.OntologyCategoryVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本体查询 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明本体只读原子操作，
 * 由 {@code McpServerConfig} 收集为 {@code ToolCallbackProvider} 注册进 MCP 协议，agent-server 端通过
 * {@code tools/list} 动态发现并按需调用。工具方法直接返回既有业务 VO，Spring AI 自动序列化为 MCP 结果；
 * 方法委托既有 Service，不含业务逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyMcpTools {

    private final OntologyMetaService ontologyMetaService;
    private final OntologyGroupService ontologyGroupService;
    private final OntologyCategoryService ontologyCategoryService;

    @Tool(description = """
            按关键词搜索本体（Ontology）元数据。
            当用户想查找、检索、列出本体，或询问"有哪些本体""某某本体是否存在"时使用。
            返回本体的名称、描述、唯一标识、所属空间及实例/关系/属性数量等信息。
            关键词为空时返回全部本体。
            """)
    public List<OntologyMetaInfoVO> searchOntologyMeta(
            @ToolParam(description = "搜索关键词，可为空；为空时返回全部本体", required = false) String keyword) {
        return ontologyMetaService.searchByKeyword(keyword);
    }

    @Tool(description = """
            根据本体唯一标识（uniqueIdentifier）查询单个本体的元数据详情。
            当用户已经知道某个本体的唯一标识，想了解该本体的详细信息时使用。
            返回该本体的名称、描述、所属空间及各类数量统计。
            """)
    public OntologyMetaInfoVO getOntologyMetaDetail(
            @ToolParam(description = "本体唯一标识 uniqueIdentifier") String uniqueIdentifier) {
        return ontologyMetaService.getMetaByUniqueIdentifier(uniqueIdentifier);
    }

    @Tool(description = """
            根据本体空间 id（spaceId）查询该空间下的所有本体分组。
            当用户想了解某个空间内有哪些分组、分组如何组织时使用。
            返回分组 id、名称、描述及所属空间 id。
            """)
    public List<OntologyGroupInfoVO> listGroupsBySpace(
            @ToolParam(description = "本体空间 id（spaceId）") Integer spaceId) {
        return ontologyGroupService.getGroupBySpaceId(spaceId);
    }

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
