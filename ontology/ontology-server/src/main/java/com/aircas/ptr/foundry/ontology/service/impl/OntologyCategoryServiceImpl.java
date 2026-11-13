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
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaStatisticVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyCategoryMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
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

        var category = getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, param.getSpaceId())
                .eq(OntologyCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(category != null, "对象分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);

        var newParent = checkParentAndGet(category, param.getParentId());

        // 查询所有关联节点（节点树）：精确匹配父节点自身，或以 "parentPath/" 为前缀的子孙节点
        var allCategories = list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, param.getSpaceId())
                .and(w -> w.eq(OntologyCategory::getPath, category.getPath())
                        .or()
                        .likeRight(OntologyCategory::getPath, category.getPath() + "/")));
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
                    // 使用前缀替换，避免 String.replace() 替换所有匹配项
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
    private OntologyCategory checkParentAndGet(OntologyCategory category, Integer parentId) {

        if (parentId == null || parentId.equals(category.getParentId())) {
            return null;
        }
        PreconditionUtils.checkArgument(!parentId.equals(category.getId()), "parentId不能是自己", HttpStatus.BAD_REQUEST);
        // 对象分类树只有一个根节点（parentId=0），移动成第二个根节点会让子树在树查询里丢失
        PreconditionUtils.checkArgument(!parentId.equals(0), "parentId不能为0，对象分类树只支持一个根节点", HttpStatus.BAD_REQUEST);

        var spaceCategories = list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, category.getOntologySpaceId()));
        var categoryMap = spaceCategories.stream().collect(Collectors.toMap(OntologyCategory::getId, v -> v));
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
    public void deleteCategory(OntologyCategoryDeleteParam param) {

        var parentCategory = getOne(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, param.getSpaceId())
                .eq(OntologyCategory::getId, param.getCategoryId()));
        PreconditionUtils.checkArgument(parentCategory != null, "对象分类节点" + param.getCategoryId() + "不存在", HttpStatus.BAD_REQUEST);

        // 查询所有关联节点（节点树）：精确匹配父节点自身，或以 "parentPath/" 为前缀的子孙节点
        var allCategories = list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, param.getSpaceId())
                .and(w -> w.eq(OntologyCategory::getPath, parentCategory.getPath())
                        .or()
                        .likeRight(OntologyCategory::getPath, parentCategory.getPath() + "/")));
        if (CollectionUtils.isNotEmpty(allCategories)) {
            var categoryIds = allCategories.stream().map(OntologyCategory::getId).collect(Collectors.toList());
            var props = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getOntologySpaceId, param.getSpaceId())
                    .in(OntologyMeta::getOntologyCategoryId, categoryIds));
            PreconditionUtils.checkArgument(CollectionUtils.isEmpty(props), "该分类节点下有关联的对象，不能删除", HttpStatus.FORBIDDEN);
            removeByIds(categoryIds);
        }

    }

    @Override
    public OntologyCategoryVO getCategoryTree(Integer spaceId) {
        long startTime = System.currentTimeMillis();
        log.info("[getCategoryTree] spaceId={} 开始查询", spaceId);
        
        // 按 id 升序取出，分组后每层 children 即为 id 从小到大
        var categories = list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .orderByAsc(OntologyCategory::getId));
        long categoryQueryTime = System.currentTimeMillis();
        log.info("[getCategoryTree] spaceId={} 查询分类耗时={}ms, 分类数={}", spaceId, categoryQueryTime - startTime, categories.size());
        
        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }
        var categoryMap = categories.stream().collect(Collectors.groupingBy(
                OntologyCategory::getParentId, LinkedHashMap::new, Collectors.toList()));
        var roots = categoryMap.get(0);
        if (CollectionUtils.isEmpty(roots)) {
            return null;
        }
        var metas = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getOntologySpaceId, spaceId)
                .orderByAsc(OntologyMeta::getId));
        long metaQueryTime = System.currentTimeMillis();
        log.info("[getCategoryTree] spaceId={} 查询对象耗时={}ms, 对象数={}", spaceId, metaQueryTime - categoryQueryTime, metas.size());

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

        // 整个空间的对象计数一次批量取数，替代逐对象 4N 次查询
        var statisticMap = ontologyMetaService.batchBuildStatistic(spaceId,
                metas.stream().map(OntologyMeta::getUniqueIdentifier).collect(Collectors.toList()));
        long statisticTime = System.currentTimeMillis();
        log.info("[getCategoryTree] spaceId={} 批量统计耗时={}ms", spaceId, statisticTime - metaQueryTime);

        var result = buildCategoryVO(roots.get(0), categoryMap, metaMap, displayMap, statisticMap);
        long buildTreeEndTime = System.currentTimeMillis();
        log.info("[getCategoryTree] spaceId={} 构建分类树耗时={}ms", spaceId, buildTreeEndTime - statisticTime);

        log.info("[getCategoryTree] spaceId={} 总耗时={}ms", spaceId, buildTreeEndTime - startTime);
        return result;
    }

    @Override
    public List<OntologyCategory> getCategoryList(Integer spaceId) {
        return list(new LambdaQueryWrapper<OntologyCategory>()
                .eq(OntologyCategory::getOntologySpaceId, spaceId)
                .orderByAsc(OntologyCategory::getId));
    }

    private OntologyCategoryVO buildCategoryVO(OntologyCategory category,
                                               Map<Integer, List<OntologyCategory>> categoryMap,
                                               Map<Integer, List<OntologyMeta>> metaMap,
                                               Map<String, String> displayMap,
                                               Map<String, OntologyMetaStatisticVO> statisticMap) {

        var vo = new OntologyCategoryVO()
                .setCategoryId(category.getId())
                .setName(category.getName());
        var metaList = metaMap.get(category.getId());

        if (CollectionUtils.isNotEmpty(metaList)) {
            var metaInfoVOList = metaList.stream().map(v -> {
                var metaInfoVO = DataConverter.convert(v);
                metaInfoVO.setParentOntologyDisplayName(displayMap.get(v.getParentUniqueIdentifier()));
                var statistic = statisticMap.get(v.getUniqueIdentifier());
                metaInfoVO.setPropertyCount(statistic.getPropertyCount())
                        .setRelationCount(statistic.getRelationCount())
                        .setActionCount(statistic.getActionCount());
                return metaInfoVO;
            }).collect(Collectors.toList());
            vo.setOntologyMetaInfos(metaInfoVOList);
        }

        var children = categoryMap.get(category.getId());
        if (CollectionUtils.isNotEmpty(children)) {
            vo.setChildren(children.stream()
                    .map(child -> buildCategoryVO(child, categoryMap, metaMap, displayMap, statisticMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }

}