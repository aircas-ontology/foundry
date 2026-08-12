package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyCategoryVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyCategoryMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OntologyCategoryServiceImpl extends ServiceImpl<OntologyCategoryMapper, OntologyCategory> implements OntologyCategoryService {

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private OntologyMetaServiceImpl ontologyMetaService;

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void createCategory(OntologyCategoryCreateParam param) {

        var existCategory = list(new LambdaQueryWrapper<OntologyCategory>().eq(OntologyCategory::getOntologySpaceId, param.getSpaceId()));
        var existCategoryMap = existCategory.stream().collect(Collectors.toMap(OntologyCategory::getId, v -> v));

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
            List<OntologyCategory> batchList = Lists.newArrayList();
            List<CategoryNode> nextLevel = Lists.newArrayList();
            for (var node : currentLevel) {
                var category = OntologyCategory.builder()
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
    public void updateCategory(OntologyCategoryUpdateParam param) {

        var parentCategory = getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, param.getSpaceId())
                .eq(OntologyCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(parentCategory != null, "本体分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);
        // 查询所有关联节点（节点树）
        var allCategories = list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, param.getSpaceId())
                .likeRight(OntologyCategory::getPath, parentCategory.getPath()));
        if (CollectionUtils.isNotEmpty(allCategories)) {
            var paths = parentCategory.getPath().split("/");
            paths[paths.length - 1] = param.getName();
            var newPath = String.join("/", paths);
            allCategories.forEach(category -> {
                if (category.getId().equals(param.getCategoryId())) {
                    category.setName(param.getName());
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
    public void deleteCategory(OntologyCategoryDeleteParam param) {

        var parentCategory = getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, param.getSpaceId())
                .eq(OntologyCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(parentCategory != null, "本体分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);

        // 查询所有关联节点（节点树）
        var allCategories = list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, param.getSpaceId())
                .likeRight(OntologyCategory::getPath, parentCategory.getPath()));
        if (CollectionUtils.isNotEmpty(allCategories)) {
            var categoryIds = allCategories.stream().map(OntologyCategory::getId).collect(Collectors.toList());
            var props = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getOntologySpaceId, param.getSpaceId())
                    .in(OntologyMeta::getOntologyCategoryId, categoryIds));
            PreconditionUtils.checkArgument(CollectionUtils.isEmpty(props), "该分类节点下有关联的本体，不能删除", HttpStatus.FORBIDDEN);
            removeByIds(categoryIds);
        }

    }

    @Override
    public OntologyCategoryVO getCategoryTree(Integer spaceId) {
        var categories = list(new LambdaQueryWrapper<OntologyCategory>().eq(OntologyCategory::getOntologySpaceId, spaceId));
        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }
        var categoryMap = categories.stream().collect(Collectors.groupingBy(OntologyCategory::getParentId));
        var roots = categoryMap.get(0);
        if (CollectionUtils.isEmpty(roots)) {
            return null;
        }
        var metas = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId));

        var metaMap = metas.stream()
                .collect(Collectors.toMap(
                        OntologyMeta::getOntologyCategoryId,
                        Collections::singletonList,
                        (list1, list2) -> {
                            List<OntologyMeta> merged = new ArrayList<>(list1);
                            merged.addAll(list2);
                            return merged;
                        },
                        HashMap::new
                ));

        var displayMap = metas.stream().collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, OntologyMeta::getDisplayName));
        return buildCategoryVO(roots.get(0), categoryMap, metaMap, displayMap);
    }

    private OntologyCategoryVO buildCategoryVO(OntologyCategory category,
                                               Map<Integer, List<OntologyCategory>> categoryMap,
                                               Map<Integer, List<OntologyMeta>> metaMap,
                                               Map<String, String> displayMap) {

        var vo = new OntologyCategoryVO()
                .setCategoryId(category.getId())
                .setName(category.getName());
        var metaList = metaMap.get(category.getId());

        if (CollectionUtils.isNotEmpty(metaList)) {
            var metaInfoVOList = metaList.stream().map(v -> {
                var metaInfoVO = DataConverter.convert(v);
                metaInfoVO.setParentOntologyDisplayName(displayMap.get(v.getParentUniqueIdentifier()));
                ontologyMetaService.buildMetaInfoStatistic(metaInfoVO);
                return metaInfoVO;
            }).collect(Collectors.toList());
            vo.setOntologyMetaInfos(metaInfoVOList);
        }

        var children = categoryMap.get(category.getId());
        if (CollectionUtils.isNotEmpty(children)) {
            vo.setChildren(children.stream()
                    .map(child -> buildCategoryVO(child, categoryMap, metaMap, displayMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }

}