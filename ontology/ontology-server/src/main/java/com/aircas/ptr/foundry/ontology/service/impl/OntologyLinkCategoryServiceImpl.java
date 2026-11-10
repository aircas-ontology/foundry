package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkCategoryLinkVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkCategoryVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkCategoryMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkCategoryService;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OntologyLinkCategoryServiceImpl extends ServiceImpl<OntologyLinkCategoryMapper, OntologyLinkCategory> implements OntologyLinkCategoryService {

    @Resource
    private OntologyLinkGroupMapper ontologyLinkGroupMapper;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    /**
     * 关系分类与对象分类采用同一套树模型：
     * parentId=0 表示根节点；path 为全路径（以 "/" 分隔），用于级联更新/删除。
     */
    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void createCategory(OntologyLinkCategoryCreateParam param) {

        var existCategory = list(new LambdaQueryWrapper<OntologyLinkCategory>().eq(OntologyLinkCategory::getOntologySpaceId, param.getSpaceId()));
        var existCategoryMap = existCategory.stream().collect(Collectors.toMap(OntologyLinkCategory::getId, v -> v));

        //校验parentId
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
            List<OntologyLinkCategory> batchList = Lists.newArrayList();
            List<CategoryNode> nextLevel = Lists.newArrayList();
            for (var node : currentLevel) {
                var category = OntologyLinkCategory.builder()
                        .ontologySpaceId(param.getSpaceId())
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
    public void updateCategory(OntologyLinkCategoryUpdateParam param) {

        var category = getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, param.getSpaceId())
                .eq(OntologyLinkCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(category != null, "关系分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);

        var newParent = checkParentAndGet(category, param.getParentId());

        // 查询所有关联节点（节点树）
        var allCategories = list(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, param.getSpaceId())
                .and(w -> w.eq(OntologyLinkCategory::getPath, category.getPath())
                        .or()
                        .likeRight(OntologyLinkCategory::getPath, category.getPath() + "/")));
        if (CollectionUtils.isNotEmpty(allCategories)) {
            var oldPath = category.getPath();
            var paths = oldPath.split("/");
            paths[paths.length - 1] = param.getName();
            // 移动到新父节点下时，路径前缀整体换成新父节点的路径
            var newPath = newParent == null ? String.join("/", paths) : newParent.getPath() + "/" + param.getName();
            allCategories.forEach(node -> {
                if (node.getId().equals(param.getCategoryId())) {
                    node.setName(param.getName());
                    node.setPath(newPath);
                    if (newParent != null) {
                        node.setParentId(newParent.getId());
                    }
                } else {
                    var updatedPath = newPath + node.getPath().substring(oldPath.length());
                    node.setPath(updatedPath);
                }
            });
            updateBatchById(allCategories);
        }
    }

    /**
     * 校验新的 parentId 并返回对应的父节点；parentId 为空或与当前父节点相同时返回 null，表示本次不移动节点。
     */
    private OntologyLinkCategory checkParentAndGet(OntologyLinkCategory category, Integer parentId) {

        if (parentId == null || parentId.equals(category.getParentId())) {
            return null;
        }
        PreconditionUtils.checkArgument(!parentId.equals(category.getId()), "parentId不能是自己", HttpStatus.BAD_REQUEST);
        // 关系分类树只有一个根节点（parentId=0），移动成第二个根节点会让子树在树查询里丢失
        PreconditionUtils.checkArgument(!parentId.equals(0), "parentId不能为0，关系分类树只支持一个根节点", HttpStatus.BAD_REQUEST);

        var spaceCategories = list(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, category.getOntologySpaceId()));
        var categoryMap = spaceCategories.stream().collect(Collectors.toMap(OntologyLinkCategory::getId, v -> v));
        var newParent = categoryMap.get(parentId);
        PreconditionUtils.checkArgument(newParent != null, "无效的parentId：" + parentId, HttpStatus.BAD_REQUEST);

        // 沿新父节点的 parentId 父链上溯，命中自己即成环（visited 兼容已有的脏父链，保证循环能退出）
        var visited = new HashSet<Integer>();
        var currentId = newParent.getParentId();
        while (currentId != null && currentId != 0 && visited.add(currentId)) {
            PreconditionUtils.checkArgument(!currentId.equals(category.getId()), "parentId不能是自己的下级节点", HttpStatus.BAD_REQUEST);
            var ancestor = categoryMap.get(currentId);
            if (ancestor == null) {
                break;
            }
            currentId = ancestor.getParentId();
        }
        return newParent;
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void deleteCategory(OntologyLinkCategoryDeleteParam param) {

        var parentCategory = getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, param.getSpaceId())
                .eq(OntologyLinkCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(parentCategory != null, "关系分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);

        // 查询所有关联节点（节点树）：精确匹配父节点自身，或以 "parentPath/" 为前缀的子孙节点
        var allCategories = list(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, param.getSpaceId())
                .and(w -> w.eq(OntologyLinkCategory::getPath, parentCategory.getPath())
                        .or()
                        .likeRight(OntologyLinkCategory::getPath, parentCategory.getPath() + "/")));

        if (CollectionUtils.isNotEmpty(allCategories)) {
            var categoryIds = allCategories.stream().map(OntologyLinkCategory::getId).collect(Collectors.toList());
            var links = ontologyLinkGroupMapper.selectList(new LambdaQueryWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getOntologySpaceId, param.getSpaceId())
                    .in(OntologyLinkGroup::getCategoryId, categoryIds));
            PreconditionUtils.checkArgument(CollectionUtils.isEmpty(links), "该分类节点下有关联的关系，不能删除", HttpStatus.FORBIDDEN);
            removeByIds(categoryIds);
        }

    }

    @Override
    public OntologyLinkCategoryVO getCategoryTree(Integer spaceId, String ontologyUniqueIdentifierFrom) {
        // 按 id 升序取出，分组后每层 children 即为 id 从小到大
        var categories = list(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                .orderByAsc(OntologyLinkCategory::getId));
        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }
        var categoryMap = categories.stream().collect(Collectors.groupingBy(
                OntologyLinkCategory::getParentId, LinkedHashMap::new, Collectors.toList()));
        var roots = categoryMap.get(0);
        if (CollectionUtils.isEmpty(roots)) {
            return null;
        }
        // 查询该空间下所有关系；未挂分类或分类已删除的关系并入根分类，避免统计里有、树里查不到
        var rootCategoryId = roots.get(0).getId();
        var linkQuery = new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologySpaceId, spaceId);
        // 可选过滤：前端传了对象唯一标识时，返回该对象在任一端（源或目标）的关系，与 /link/graph 的双向口径一致
        if (ontologyUniqueIdentifierFrom != null && !ontologyUniqueIdentifierFrom.isBlank()) {
            linkQuery.and(w -> w.eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifierFrom)
                    .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifierFrom));
        }
        var links = ontologyLinkGroupMapper.selectList(linkQuery);
        var knownCategoryIds = categories.stream().map(OntologyLinkCategory::getId).collect(Collectors.toSet());
        Map<Integer, List<OntologyLinkGroup>> linkMap = links.stream()
                .collect(Collectors.groupingBy(l -> l.getCategoryId() != null && knownCategoryIds.contains(l.getCategoryId())
                        ? l.getCategoryId() : rootCategoryId));

        // 收集关系两端对象的 uniqueIdentifier，一次性查出对象名称，供 linkVO 填充 ontologyNameFrom/To
        Set<String> uidSet = new HashSet<>();
        links.forEach(l -> {
            if (l.getOntologyUniqueIdentifierFrom() != null) uidSet.add(l.getOntologyUniqueIdentifierFrom());
            if (l.getOntologyUniqueIdentifierTo() != null) uidSet.add(l.getOntologyUniqueIdentifierTo());
        });
        Map<String, OntologyMeta> metaMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(uidSet)) {
            metaMap = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                            .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                            .in(OntologyMeta::getUniqueIdentifier, uidSet))
                    .stream()
                    .collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, v -> v, (a, b) -> a));
        }
        return buildCategoryVO(roots.get(0), categoryMap, linkMap, metaMap);
    }

    private OntologyLinkCategoryVO buildCategoryVO(OntologyLinkCategory category,
                                                  Map<Integer, List<OntologyLinkCategory>> categoryMap,
                                                  Map<Integer, List<OntologyLinkGroup>> linkMap,
                                                  Map<String, OntologyMeta> metaMap) {

        var vo = OntologyLinkCategoryVO.builder()
                .categoryId(category.getId())
                .name(category.getName())
                .build();
        var linkList = linkMap.get(category.getId());

        if (CollectionUtils.isNotEmpty(linkList)) {
            var linkVOList = linkList.stream().map(v -> {
                var from = metaMap.get(v.getOntologyUniqueIdentifierFrom());
                var to = metaMap.get(v.getOntologyUniqueIdentifierTo());
                return OntologyLinkCategoryLinkVO.builder()
                        .uniqueIdentifier(v.getUniqueIdentifier())
                        .name(v.getName())
                        .type(v.getType() == null ? null : v.getType().name())
                        // 回显挂载节点 id：未分类关系已并入根分类，若原样返回 null 前端会把这条关系丢掉
                        .categoryId(category.getId())
                        .ontologyUniqueIdentifierFrom(v.getOntologyUniqueIdentifierFrom())
                        .ontologyNameFrom(from != null ? from.getDisplayName() : null)
                        .ontologyUniqueIdentifierTo(v.getOntologyUniqueIdentifierTo())
                        .ontologyNameTo(to != null ? to.getDisplayName() : null)
                        .ontologyIconFrom(from != null ? from.getIcon() : null)
                        .ontologyIconTO(to != null ? to.getIcon() : null)
                        .apiName(v.getApiName() == null ? null : v.getApiName())
                        .description(v.getDescription() == null ? null : v.getDescription())
                        .build();
            }).collect(Collectors.toList());
            vo.setLinks(linkVOList);
        }

        var children = categoryMap.get(category.getId());
        if (CollectionUtils.isNotEmpty(children)) {
            vo.setChildren(children.stream()
                    .map(child -> buildCategoryVO(child, categoryMap, linkMap, metaMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }

    @Override
    public List<OntologyLinkCategory> getCategoryList(Integer spaceId) {
        return list(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                .orderByAsc(OntologyLinkCategory::getId));
    }

}
