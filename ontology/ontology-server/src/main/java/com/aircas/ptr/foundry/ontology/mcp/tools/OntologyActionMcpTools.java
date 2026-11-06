package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionStatusEnum;
import com.aircas.ptr.foundry.ontology.model.po.ActionCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyActionInfoVO;
import com.aircas.ptr.foundry.ontology.service.ActionCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对象行为 MCP 工具。
 *
 * <p>提供空间+行为名称的组合精确查询，由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyActionMcpTools implements McpTools {

    private final OntologySpaceService ontologySpaceService;
    private final OntologyActionService ontologyActionService;
    private final ActionCategoryService actionCategoryService;

    @Tool(description = """
            按空间名称 + 行为名称精确查询对象行为。
            当用户已知空间和行为的名称，需要查找行为的详细信息时使用。
            先按空间名定位空间，再在该空间内按行为名精确匹配。
            行为名同时匹配 displayName 和 api（OR 关系）；不传行为名时返回该空间下的全部行为。
            无匹配时返回空列表。
            """)
    public List<OntologyActionInfoVO> queryActions(
            @ToolParam(description = "空间名称（apiName 或 displayName）") String spaceName,
            @ToolParam(description = "行为名称（displayName 或 api），可为空；为空时返回该空间下全部行为", required = false) String actionName) {

        if (StringUtils.isEmpty(spaceName)) {
            return Collections.emptyList();
        }

        var spaces = ontologySpaceService.list(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getApiName, spaceName)
                .or().eq(OntologySpace::getDisplayName, spaceName));
        if (CollectionUtils.isEmpty(spaces)) {
            return Collections.emptyList();
        }
        var spaceIds = spaces.stream().map(OntologySpace::getId).collect(Collectors.toList());

        var queryWrapper = new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getStatus, ActionStatusEnum.PUBLISHED.getValue())
                .in(OntologyAction::getOntologySpaceId, spaceIds);
        // 行为树不是单个可调用的动作，也没有绑定算子，暴露给 Agent 只会拿到一个无法执行的空工具。
        // 类别为空的存量行为要保留，所以只排掉 TREE，不能写成 ne（SQL 里 NULL 不参与比较）
        queryWrapper.and(w -> w.isNull(OntologyAction::getActionKind)
                .or().ne(OntologyAction::getActionKind, ActionKindEnum.TREE));
        if (StringUtils.isNotEmpty(actionName)) {
            queryWrapper.and(w -> w.eq(OntologyAction::getDisplayName, actionName)
                    .or().eq(OntologyAction::getApi, actionName));
        }

        var actions = ontologyActionService.list(queryWrapper);
        if (CollectionUtils.isEmpty(actions)) {
            return Collections.emptyList();
        }

        return actions.stream()
                .map(a -> OntologyActionInfoVO.builder()
                        .icon(a.getIcon())
                        .ontologyUniqIdentifier(a.getOntologyUniqueIdentifier())
                        .actionApi(a.getApi())
                        .description(a.getDescription())
                        .displayName(a.getDisplayName())
                        .functionApi(a.getFunctionApi())
                        .build())
                .collect(Collectors.toList());
    }

    @Tool(description = """
            根据对象空间 id（spaceId）查询该空间下的行为分类列表（扁平结构，不分页）。
            当用户需要查看空间下所有行为分类的完整列表时使用。
            返回分类 id、名称、父分类 id 等完整信息。
            """)
    public List<ActionCategory> listActionCategories(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId) {
        return actionCategoryService.getCategoryList(spaceId);
    }
}
