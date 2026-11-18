package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.vo.ActionCategoryVO;
import com.aircas.ptr.foundry.ontology.service.ActionCategoryService;
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
 * 行为分类体系 MCP 工具。
 *
 * <p>覆盖 {@code ActionCategoryController}（/action_category）的建树/改名/删树/查树端点。
 * 作用域为「对象空间」：同一空间共用一棵行为分类体系树，与对象分类（OntologyCategoryMcpTools）、
 * 属性分类、关系分类互不相干。扁平分类列表端点 GET /action_category/list 已由
 * {@code OntologyActionMcpTools#listActionCategories} 覆盖，这里不重复提供。
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class ActionCategoryMcpTools implements McpTools {

    private final ActionCategoryService actionCategoryService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            创建行为分类体系树节点（对应 POST /action_category）。写操作：按层级批量写入行为分类表。
            当需要为空间下的行为建立分类体系（如"态势感知/轨道机动"）时使用，可携带子分类一次建出整棵子树。
            作用域为对象空间：一个空间只有一棵行为分类体系树。
            parentId 传 0 表示创建根节点（仅当该空间还没有任何行为分类时允许）；传已有分类 id 则在其下创建子分类。
            childrenJson 为可选的子分类 JSON 数组字符串，元素形如 {"name":"子分类","children":[...]}，可递归嵌套，每个节点 name 必填。
            参数：spaceId 为对象空间 id（必填，空间须存在）；parentId 为父分类 id（必填，0 表示新建体系根节点）；name 为分类名称（必填）。
            返回创建结果说明字符串。parentId 无效（根节点重复建、父节点不存在）时抛错，不落库。
            """)
    public String createActionCategory(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId,
            @ToolParam(description = "父分类 id；0 表示创建根节点（仅当空间还没有行为分类时允许）") Integer parentId,
            @ToolParam(description = "行为分类名称") String name,
            @ToolParam(description = "子分类 JSON 数组字符串，元素形如 {\"name\":\"子分类\",\"children\":[...]}，可为空", required = false) String childrenJson) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空，应为对象空间 id");
        }
        if (parentId == null) {
            throw new IllegalArgumentException("parentId 不能为空，0 表示创建根节点");
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name 不能为空，应为行为分类名称");
        }
        ActionCategoryCreateParam param = new ActionCategoryCreateParam();
        param.setSpaceId(spaceId);
        param.setParentId(parentId);
        param.setName(name);
        if (StringUtils.isNotBlank(childrenJson)) {
            param.setChildren(parseChildrenJson(childrenJson));
        }
        actionCategoryService.createActionCategory(param);
        return "行为分类创建成功：" + name + "（spaceId=" + spaceId + "，parentId=" + parentId + "）";
    }

    @Tool(description = """
            修改行为分类的名称或父节点（对应 PUT /action_category）。写操作：更新分类并级联重写整棵子树的路径。
            当需要重命名行为分类、或把分类移动到其他分类下时使用。
            参数：spaceId 为对象空间 id（必填）；categoryId 为要修改的行为分类 id（必填）；name 为新的分类名称（必填）。
            parentId 可选：不传表示不移动父节点；传值时不能是分类自己，也不能是自己的下级节点（会成环）。
            返回更新结果说明字符串。分类不存在或移动目标不合法时抛错。
            """)
    public String updateActionCategory(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId,
            @ToolParam(description = "要修改的行为分类 id") Integer categoryId,
            @ToolParam(description = "新的分类名称") String name,
            @ToolParam(description = "新的父分类 id，不传表示不移动；不能是自身或自身的下级节点", required = false) Integer parentId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空，应为对象空间 id");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId 不能为空，应为要修改的行为分类 id");
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name 不能为空，应为新的分类名称");
        }
        ActionCategoryUpdateParam param = new ActionCategoryUpdateParam();
        param.setSpaceId(spaceId);
        param.setCategoryId(categoryId);
        param.setName(name);
        param.setParentId(parentId);
        actionCategoryService.updateActionCategory(param);
        return "行为分类更新成功：" + name + "（categoryId=" + categoryId + "）";
    }

    @Tool(description = """
            删除行为分类树节点（对应 DELETE /action_category，body 参数已平铺）。写操作：物理删除该分类及其整棵子树，不可恢复，执行前应与用户确认。
            当需要移除行为分类时使用。
            限制：该节点或其子孙分类下仍挂载行为时拒绝删除，需先把行为改到其他分类（编辑行为的 categoryId）后再删。
            参数：spaceId 为对象空间 id（必填）；categoryId 为要删除的行为分类 id（必填）。
            返回删除结果说明字符串。分类不存在或分类下仍有行为时抛错。
            """)
    public String deleteActionCategory(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId,
            @ToolParam(description = "要删除的行为分类 id") Integer categoryId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空，应为对象空间 id");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId 不能为空，应为要删除的行为分类 id");
        }
        ActionCategoryDeleteParam param = new ActionCategoryDeleteParam();
        param.setSpaceId(spaceId);
        param.setCategoryId(categoryId);
        actionCategoryService.deleteActionCategory(param);
        return "行为分类删除成功：categoryId=" + categoryId + "（含其全部子孙分类）";
    }

    @Tool(description = """
            查询空间的行为分类体系树（对应 GET /action_category/tree）。只读操作。
            当需要查看行为分类的完整树形结构及各分类下挂载的行为时使用；只需扁平分类列表（拿 categoryId 建行为/筛选行为）时用 listActionCategories。
            参数 spaceId 为对象空间 id（必填，空间须存在）。
            返回 ActionCategoryVO 树：categoryId 分类 id、name 分类名称、actionInfos 该分类下的行为列表
            （含 actionApi、displayName、description 等）、children 子分类（递归结构，按 id 升序）。
            行为范围按本空间下的对象收敛，行为树按空间并入。空间还没有行为分类体系时返回 null。
            """)
    public ActionCategoryVO getActionCategoryTree(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空，应为对象空间 id");
        }
        return actionCategoryService.getActionCategoryTree(spaceId);
    }

    /** MCP 无参数校验切面，子树入参走 JSON 字符串 + Jackson 反序列化，空白与格式错误在这里防护 */
    private List<CategoryNode> parseChildrenJson(String childrenJson) {
        try {
            return jsonMapper.readValue(childrenJson, new TypeReference<List<CategoryNode>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("childrenJson 解析失败，应为 JSON 数组字符串，元素形如 {\"name\":\"子分类\",\"children\":[...]}：" + e.getMessage());
        }
    }
}