package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.po.ActionCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.ActionCategoryVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyActionInfoVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionCategoryMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.ActionCategoryService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 行为分类体系树服务实现。
 * <p>
 * 实现范式对齐 {@link OntologyCategoryServiceImpl}：
 * <ul>
 *     <li>树模型：parent_id（邻接表） + path（物化路径），子树操作统一用 path 前缀扫描；</li>
 *     <li>创建：按层级 BFS，每层 saveBatch 后取回自增 id 再装配下一层；</li>
 *     <li>重命名：整体替换 path 前缀，一次性 updateBatchById；</li>
 *     <li>删除：级联整棵子树，但先做下游引用校验。</li>
 * </ul>
 * 作用域为「本体空间」（{@code unique(ontology_space_id, path)}），与 {@link OntologyCategoryServiceImpl} 一致，
 * 即同一空间共用一棵行为分类体系树；删除时的引用校验对象是行为（{@code ontology_action}）。
 * <p>
 * 注意：行为到空间的归属需经「本体 → 空间」桥接，见 {@link #listOntologyIdentifiersOfSpace(Integer)}。
 */
@Service
@Slf4j
public class ActionCategoryServiceImpl extends ServiceImpl<ActionCategoryMapper, ActionCategory> implements ActionCategoryService {

    @Resource
    private OntologyActionMapper ontologyActionMapper;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private FunctionService functionService;

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void createActionCategory(ActionCategoryCreateParam param) {

        var spaceId = param.getSpaceId();
        var existCategoryList = list(new LambdaQueryWrapper<ActionCategory>()
                .eq(ActionCategory::getOntologySpaceId, spaceId));
        var existCategoryMap = existCategoryList.stream()
                .collect(Collectors.toMap(ActionCategory::getId, v -> v));

        // 校验 parentId：为 0 时要求该空间下尚无行为分类（即一个空间只有一棵行为分类体系树），否则父节点必须存在
        var parentId = param.getParentId();
        if (!((parentId.equals(0) && MapUtils.isEmpty(existCategoryMap))
                || (!parentId.equals(0) && existCategoryMap.containsKey(parentId)))) {
            throw new BusinessException("无效的parentId：" + parentId, HttpStatus.BAD_REQUEST);
        }

        var parentPath = parentId.equals(0) ? "" : existCategoryMap.get(parentId).getPath() + "/";

        var rootNode = CategoryNode.builder()
                .parentId(parentId)
                .children(param.getChildren())
                .name(param.getName())
                .path(parentPath + param.getName())
                .build();

        // 按层级 BFS，每层批量插入
        List<CategoryNode> currentLevel = Lists.newArrayList(rootNode);

        while (CollectionUtils.isNotEmpty(currentLevel)) {
            List<ActionCategory> batchList = Lists.newArrayList();
            List<CategoryNode> nextLevel = Lists.newArrayList();
            for (var node : currentLevel) {
                var category = ActionCategory.builder()
                        .ontologySpaceId(spaceId)
                        .name(node.getName())
                        .parentId(node.getParentId())
                        .path(node.getPath())
                        .build();
                batchList.add(category);
            }
            // 当前层级批量插入
            saveBatch(batchList);
            // 拿到自增 ID 后，构建下一层节点
            for (int i = 0; i < currentLevel.size(); i++) {
                var node = currentLevel.get(i);
                var generatedId = batchList.get(i).getId();
                if (CollectionUtils.isNotEmpty(node.getChildren())) {
                    for (var child : node.getChildren()) {
                        var childNode = CategoryNode.builder()
                                .parentId(generatedId)
                                .path(node.getPath() + "/" + child.getName())
                                .name(child.getName())
                                .children(child.getChildren())
                                .build();
                        nextLevel.add(childNode);
                    }
                }
            }
            currentLevel = nextLevel;
        }
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void updateActionCategory(ActionCategoryUpdateParam param) {

        var spaceId = param.getSpaceId();
        var currentCategory = getOne(new LambdaQueryWrapper<ActionCategory>()
                .eq(ActionCategory::getOntologySpaceId, spaceId)
                .eq(ActionCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(currentCategory != null,
                "行为分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);

        // 查询所有关联节点（节点树）：精确匹配自身 + 带 "/" 边界的前缀匹配，
        // 使「舰艇机动」不会命中「舰艇机动性」这类同前缀兄弟节点。
        var allCategories = list(new LambdaQueryWrapper<ActionCategory>()
                .eq(ActionCategory::getOntologySpaceId, spaceId)
                .and(w -> w.eq(ActionCategory::getPath, currentCategory.getPath())
                        .or()
                        .likeRight(ActionCategory::getPath, currentCategory.getPath() + "/")));
        if (CollectionUtils.isNotEmpty(allCategories)) {
            var paths = currentCategory.getPath().split("/");
            paths[paths.length - 1] = param.getName();
            var newPath = String.join("/", paths);
            var oldPath = currentCategory.getPath();
            allCategories.forEach(category -> {
                if (category.getId().equals(param.getCategoryId())) {
                    category.setName(param.getName());
                }
                // 仅重写 path 的前缀部分，保留子孙路径的后半段
                var categoryPath = category.getPath();
                var updatedPath = categoryPath.startsWith(oldPath)
                        ? newPath + categoryPath.substring(oldPath.length())
                        : categoryPath;
                category.setPath(updatedPath);
            });
            updateBatchById(allCategories);
        }
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void deleteActionCategory(ActionCategoryDeleteParam param) {

        var spaceId = param.getSpaceId();
        var currentCategory = getOne(new LambdaQueryWrapper<ActionCategory>()
                .eq(ActionCategory::getOntologySpaceId, spaceId)
                .eq(ActionCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(currentCategory != null,
                "行为分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);

        // 查询所有关联节点（节点树）：精确匹配自身 + 带 "/" 边界的前缀匹配，
        // 使「舰艇机动」不会命中「舰艇机动性」这类同前缀兄弟节点。
        var allCategories = list(new LambdaQueryWrapper<ActionCategory>()
                .eq(ActionCategory::getOntologySpaceId, spaceId)
                .and(w -> w.eq(ActionCategory::getPath, currentCategory.getPath())
                        .or()
                        .likeRight(ActionCategory::getPath, currentCategory.getPath() + "/")));
        if (CollectionUtils.isNotEmpty(allCategories)) {
            var categoryIds = allCategories.stream().map(ActionCategory::getId).collect(Collectors.toList());
            // 待删节点已由 spaceId 限定在本空间内，故行为侧只需按分类 id 命中即可
            var relatedActions = ontologyActionMapper.selectList(new LambdaQueryWrapper<OntologyAction>()
                    .in(OntologyAction::getActionCategoryId, categoryIds));
            PreconditionUtils.checkArgument(CollectionUtils.isEmpty(relatedActions),
                    "该行为分类节点下有关联的行为，不能删除", HttpStatus.FORBIDDEN);
            removeByIds(categoryIds);
        }
    }

    @Override
    public ActionCategoryVO getActionCategoryTree(Integer spaceId) {

        var categories = list(new LambdaQueryWrapper<ActionCategory>()
                .eq(ActionCategory::getOntologySpaceId, spaceId));
        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }
        var categoryMap = categories.stream().collect(Collectors.groupingBy(ActionCategory::getParentId));
        var roots = categoryMap.get(0);
        if (CollectionUtils.isEmpty(roots)) {
            return null;
        }

        // 以本空间下的本体集合收敛行为的挂载范围（行为到空间的归属经本体解析）
        var ontologyIdentifiers = listOntologyIdentifiersOfSpace(spaceId);
        var actions = CollectionUtils.isEmpty(ontologyIdentifiers)
                ? Collections.<OntologyAction>emptyList()
                : ontologyActionMapper.selectList(new LambdaQueryWrapper<OntologyAction>()
                        .in(OntologyAction::getOntologyUniqueIdentifier, ontologyIdentifiers));
        var actionMap = actions.stream()
                .filter(action -> action.getActionCategoryId() != null)
                .collect(Collectors.groupingBy(OntologyAction::getActionCategoryId));

        // 一次批量取函数描述，避免在递归装配 VO 时逐条查函数表
        var functionDescMap = functionService.mapDescriptionByApi(actions.stream()
                .map(OntologyAction::getFunctionApi)
                .collect(Collectors.toList()));

        return buildCategoryVO(roots.get(0), categoryMap, actionMap, functionDescMap);
    }

    /**
     * 取本空间下的全部本体唯一标识。
     * <p>
     * 行为的空间归属以本体为准：行为表上的空间列不由本服务维护，
     * 因此统一经本体（{@code ontology_meta.ontology_space_id}）解析，避免两处空间值不一致。
     */
    private List<String> listOntologyIdentifiersOfSpace(Integer spaceId) {
        return ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, spaceId))
                .stream()
                .map(OntologyMeta::getUniqueIdentifier)
                .collect(Collectors.toList());
    }

    private ActionCategoryVO buildCategoryVO(ActionCategory category,
                                             Map<Integer, List<ActionCategory>> categoryMap,
                                             Map<Integer, List<OntologyAction>> actionMap,
                                             Map<String, String> functionDescMap) {

        var vo = new ActionCategoryVO()
                .setCategoryId(category.getId())
                .setName(category.getName());

        var actionList = actionMap.get(category.getId());
        if (CollectionUtils.isNotEmpty(actionList)) {
            vo.setActionInfos(actionList.stream()
                    .map(v -> OntologyActionInfoVO.builder()
                            .actionApi(v.getApi())
                            .description(v.getDescription())
                            .ontologyUniqIdentifier(v.getOntologyUniqueIdentifier())
                            .displayName(v.getDisplayName())
                            .icon(v.getIcon())
                            .functionApi(v.getFunctionApi())
                            .functionDescription(functionDescMap.get(v.getFunctionApi()))
                            .build())
                    .collect(Collectors.toList()));
        }

        var children = categoryMap.get(category.getId());
        if (CollectionUtils.isNotEmpty(children)) {
            vo.setChildren(children.stream()
                    .map(child -> buildCategoryVO(child, categoryMap, actionMap, functionDescMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }

}
