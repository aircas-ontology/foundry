package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyCategoryVO;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 对象分类 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明对象分类体系树的
 * 查询与增删改原子操作，由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。
 * 方法委托既有 Service，直接返回自有业务 VO，Spring AI 自动序列化为 MCP 结果。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyCategoryMcpTools implements McpTools {

    private final OntologyCategoryService ontologyCategoryService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            根据对象空间 id（spaceId）查询该空间下的分类列表（拍平）。
            当用户需要为对象选择分类时使用。
            返回分类 id 和名称列表。
            """)
    public List<OntologyCategoryVO> listCategoriesBySpace(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId) {
        // Service 返回分类树，此处拍平为仅含 categoryId / name 的扁平列表，便于大模型选择分类
        OntologyCategoryVO tree = ontologyCategoryService.getCategoryTree(spaceId);
        List<OntologyCategoryVO> flat = new ArrayList<>();
        flatten(tree, flat);
        return flat;
    }

    @Tool(description = """
            根据对象空间 id（spaceId）查询该空间下的分类列表（扁平结构，不分页）。
            当用户需要查看空间下所有对象分类的完整列表时使用。
            返回分类 id、名称、父分类 id 等完整信息。
            """)
    public List<OntologyCategory> listCategoriesFlat(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId) {
        return ontologyCategoryService.getCategoryList(spaceId);
    }

    @Tool(description = """
            根据对象空间 id（spaceId）查询该空间的分类体系树（含层级 children 与绑定对象）。
            当用户需要查看分类的完整树形结构时使用；只需扁平列表时用 listCategoriesFlat。
            """)
    public OntologyCategoryVO getCategoryTree(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId) {
        return ontologyCategoryService.getCategoryTree(spaceId);
    }

    @Tool(description = """
            在对象空间的分类体系树中创建分类节点，可携带子分类一次建出子树。
            parentId 传 0 表示创建根节点（仅当该空间还没有分类时允许）；传已有分类 id 则在其下创建子分类。
            childrenJson 为可选的子分类 JSON 数组，元素形如 {"name":"子分类","children":[...]}，可递归嵌套。
            返回创建结果说明。
            """)
    public String createCategory(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId,
            @ToolParam(description = "父分类 id；0 表示创建根节点") Integer parentId,
            @ToolParam(description = "分类名称") String name,
            @ToolParam(description = "子分类 JSON 数组字符串，可为空", required = false) String childrenJson) {
        OntologyCategoryCreateParam param = new OntologyCategoryCreateParam();
        param.setSpaceId(spaceId);
        param.setParentId(parentId);
        param.setName(name);
        param.setChildren(parseChildren(childrenJson));
        ontologyCategoryService.createCategory(param);
        return "分类创建成功：" + name;
    }

    @Tool(description = """
            更新对象空间分类体系树中的分类：重命名，或移动到新的父分类下。
            parentId 不传表示不移动；不能移动到自身或自身的下级节点下，也不能传 0（分类树只支持一个根节点）。
            返回更新结果说明。
            """)
    public String updateCategory(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId,
            @ToolParam(description = "要更新的分类 id") Integer categoryId,
            @ToolParam(description = "新的分类名称") String name,
            @ToolParam(description = "新的父分类 id，不传表示不移动", required = false) Integer parentId) {
        OntologyCategoryUpdateParam param = new OntologyCategoryUpdateParam();
        param.setSpaceId(spaceId);
        param.setCategoryId(categoryId);
        param.setName(name);
        param.setParentId(parentId);
        ontologyCategoryService.updateCategory(param);
        return "分类更新成功：" + name;
    }

    @Tool(description = """
            删除对象空间分类体系树中的分类节点，连同其全部子孙分类一起删除。
            该节点或其子孙分类下仍绑定对象时不允许删除，需先解除对象绑定。
            返回删除结果说明。
            """)
    public String deleteCategory(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId,
            @ToolParam(description = "要删除的分类 id") Integer categoryId) {
        OntologyCategoryDeleteParam param = new OntologyCategoryDeleteParam();
        param.setSpaceId(spaceId);
        param.setCategoryId(categoryId);
        ontologyCategoryService.deleteCategory(param);
        return "分类删除成功，categoryId=" + categoryId;
    }

    private List<CategoryNode> parseChildren(String childrenJson) {
        if (StringUtils.isBlank(childrenJson)) {
            return null;
        }
        try {
            return jsonMapper.readValue(childrenJson, new TypeReference<List<CategoryNode>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("childrenJson 解析失败，应为 [{\"name\":\"分类名\",\"children\":[...]}] 形式的 JSON 数组：" + e.getMessage());
        }
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
