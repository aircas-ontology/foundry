package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkCategoryVO;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkCategoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 关系分类体系 MCP 工具。
 *
 * <p>用 Spring AI {@link Tool} / {@link ToolParam} 声明关系分类体系树的查询与增删改原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。
 * 方法委托既有 {@link OntologyLinkCategoryService}，直接返回业务 VO 或中文结果字符串。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyLinkCategoryMcpTools implements McpTools {

    private final OntologyLinkCategoryService ontologyLinkCategoryService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            在对象空间的关系分类体系树中创建分类节点，可携带子分类一次建出子树。
            当用户需要为对象关系建立分类体系（如"亲属关系""协同关系"等分类）时使用。
            参数说明：
            - spaceId：对象空间 id，整数，必填。
            - parentId：父分类 id，整数，必填；传 0 表示创建根节点（新建关系分类体系，仅当该空间还没有关系分类时允许），
              传已有分类 id 则在其下创建子分类。
            - name：分类名称，字符串，必填。
            - childrenJson：可选的子分类 JSON 数组字符串，元素形如 {"name":"子分类","children":[...]}，可递归嵌套；不传表示只建当前节点。
            写操作副作用：在库中新增分类节点（含子树）。
            返回中文结果说明。
            """)
    public String createLinkCategory(
            @ToolParam(description = "对象空间 id（spaceId），必填") Integer spaceId,
            @ToolParam(description = "父分类 id；0 表示创建根节点（新建关系分类体系）") Integer parentId,
            @ToolParam(description = "分类名称，必填") String name,
            @ToolParam(description = "子分类 JSON 数组字符串，形如 [{\"name\":\"子分类\",\"children\":[...]}]，可为空", required = false) String childrenJson) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        if (parentId == null) {
            throw new IllegalArgumentException("parentId 不能为空，创建根节点请传 0");
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name 不能为空");
        }
        OntologyLinkCategoryCreateParam param = new OntologyLinkCategoryCreateParam();
        param.setSpaceId(spaceId);
        param.setParentId(parentId);
        param.setName(name);
        param.setChildren(parseChildren(childrenJson));
        ontologyLinkCategoryService.createCategory(param);
        return "关系分类创建成功：" + name;
    }

    @Tool(description = """
            修改对象空间关系分类体系树中的分类：重命名，或移动到新的父分类下。
            当用户需要给关系分类改名或调整层级归属时使用。
            参数说明：
            - spaceId：对象空间 id，整数，必填。
            - categoryId：要修改的分类 id（ontology_link_category.id），整数，必填。
            - name：新的分类名称，字符串，必填。
            - parentId：新的父分类 id，整数，选填；不传表示不移动。不能移动到自身或自身的下级节点下。
            写操作副作用：直接修改库中分类记录。
            返回中文结果说明。
            """)
    public String updateLinkCategory(
            @ToolParam(description = "对象空间 id（spaceId），必填") Integer spaceId,
            @ToolParam(description = "要修改的关系分类 id，必填") Integer categoryId,
            @ToolParam(description = "新的分类名称，必填") String name,
            @ToolParam(description = "新的父分类 id，不传表示不移动；不能是自身或自身的下级节点", required = false) Integer parentId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId 不能为空");
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name 不能为空");
        }
        OntologyLinkCategoryUpdateParam param = new OntologyLinkCategoryUpdateParam();
        param.setSpaceId(spaceId);
        param.setCategoryId(categoryId);
        param.setName(name);
        param.setParentId(parentId);
        ontologyLinkCategoryService.updateCategory(param);
        return "关系分类更新成功：" + name;
    }

    @Tool(description = """
            删除对象空间关系分类体系树中的分类节点，连同其全部子孙分类一起删除。
            当用户明确要求删除某个关系分类（子树）时使用。
            参数说明：
            - spaceId：对象空间 id，整数，必填。
            - categoryId：要删除的分类 id（ontology_link_category.id），整数，必填。
            写操作副作用：分类节点及其子孙分类被删除，不可恢复；
            若分类下仍挂有关系，服务端可能拒绝删除并报错，需先调整关系的分类归属。
            返回中文结果说明。
            """)
    public String deleteLinkCategory(
            @ToolParam(description = "对象空间 id（spaceId），必填") Integer spaceId,
            @ToolParam(description = "要删除的关系分类 id，必填") Integer categoryId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId 不能为空");
        }
        OntologyLinkCategoryDeleteParam param = new OntologyLinkCategoryDeleteParam();
        param.setSpaceId(spaceId);
        param.setCategoryId(categoryId);
        ontologyLinkCategoryService.deleteCategory(param);
        return "关系分类删除成功，categoryId=" + categoryId;
    }

    @Tool(description = """
            查询对象空间的关系分类体系树（含层级 children）。
            当用户需要查看关系分类的完整树形结构、或为关系挑选分类时使用；只需扁平列表时用 listLinkCategories。
            参数说明：
            - spaceId：对象空间 id，整数，必填。
            - ontologyUniqueIdentifierFrom：对象唯一标识，字符串，选填；传入时返回该对象作为源或目标任意一端
              关联的关系所涉及的分类树，不传返回该空间完整分类树。
            返回 OntologyLinkCategoryVO 树形结构：分类 id、名称、children 子分类列表等。
            空间尚未建立关系分类时返回 null 或空树。
            """)
    public OntologyLinkCategoryVO getLinkCategoryTree(
            @ToolParam(description = "对象空间 id（spaceId），必填") Integer spaceId,
            @ToolParam(description = "对象唯一标识，选填；传入时只返回该对象任一端关联关系涉及的分类树", required = false) String ontologyUniqueIdentifierFrom) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        return ontologyLinkCategoryService.getCategoryTree(spaceId, ontologyUniqueIdentifierFrom);
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
}