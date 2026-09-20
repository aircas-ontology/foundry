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
     * 关系分类与本体分类采用同一套树模型：
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
                .color(param.getColor())
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
                        .color(node.getColor())
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
                                .color(child.getColor())
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

        var parentCategory = getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, param.getSpaceId())
                .eq(OntologyLinkCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(parentCategory != null, "关系分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);
        // 查询所有关联节点（节点树）
        var allCategories = list(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, param.getSpaceId())
                .likeRight(OntologyLinkCategory::getPath, parentCategory.getPath()));
        if (CollectionUtils.isNotEmpty(allCategories)) {
            var paths = parentCategory.getPath().split("/");
            paths[paths.length - 1] = param.getName();
            var newPath = String.join("/", paths);
            allCategories.forEach(category -> {
                if (category.getId().equals(param.getCategoryId())) {
                    category.setName(param.getName());
                    // color 为可选修改：仅在请求传了 color（含空串，用于清空）时才更新
                    if (param.getColor() != null) {
                        category.setColor(param.getColor());
                    }
                }
                //更新节点new path
                var updatedPath = category.getPath().replace(parentCategory.getPath(), newPath);
                category.setPath(updatedPath);
            });
            updateBatchById(allCategories);
        }
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void deleteCategory(OntologyLinkCategoryDeleteParam param) {

        var parentCategory = getOne(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, param.getSpaceId())
                .eq(OntologyLinkCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(parentCategory != null, "关系分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);

        // 查询所有关联节点（节点树）
        var allCategories = list(new LambdaQueryWrapper<OntologyLinkCategory>()
                .eq(OntologyLinkCategory::getOntologySpaceId, param.getSpaceId())
                .likeRight(OntologyLinkCategory::getPath, parentCategory.getPath()));
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
    public OntologyLinkCategoryVO getCategoryTree(Integer spaceId) {
        var categories = list(new LambdaQueryWrapper<OntologyLinkCategory>().eq(OntologyLinkCategory::getOntologySpaceId, spaceId));
        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }
        var categoryMap = categories.stream().collect(Collectors.groupingBy(OntologyLinkCategory::getParentId));
        var roots = categoryMap.get(0);
        if (CollectionUtils.isEmpty(roots)) {
            return null;
        }
        // 查询该空间下所有挂载到分类的关系（status=1 有效），按分类id分组
        var categoryIds = categories.stream().map(OntologyLinkCategory::getId).collect(Collectors.toList());
        var links = ontologyLinkGroupMapper.selectList(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getStatus, 1)
                .eq(OntologyLinkGroup::getOntologySpaceId, spaceId)
                .in(OntologyLinkGroup::getCategoryId, categoryIds));
        Map<Integer, List<OntologyLinkGroup>> linkMap = links.stream()
                .filter(l -> l.getCategoryId() != null)
                .collect(Collectors.groupingBy(OntologyLinkGroup::getCategoryId));

        // 收集关系两端本体的 uniqueIdentifier，一次性查出本体名称，供 linkVO 填充 ontologyNameFrom/To
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
                .color(category.getColor())
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
                        .categoryId(v.getCategoryId())
                        .ontologyUniqueIdentifierFrom(v.getOntologyUniqueIdentifierFrom())
                        .ontologyNameFrom(from != null ? from.getDisplayName() : null)
                        .ontologyUniqueIdentifierTo(v.getOntologyUniqueIdentifierTo())
                        .ontologyNameTo(to != null ? to.getDisplayName() : null)
                        .ontologyIconFrom(from != null ? from.getIcon() : null)
                        .ontologyIconTO(to != null ? to.getIcon() : null)
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

}
