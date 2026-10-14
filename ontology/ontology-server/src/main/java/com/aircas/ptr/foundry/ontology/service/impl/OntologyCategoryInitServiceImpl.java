package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.po.PropertyCategory;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryInitService;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.aircas.ptr.foundry.ontology.service.PropertyCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本体/关系/属性分类树根节点初始化实现。
 *
 * <p>每个空间：建对象分类根节点“全部” + 关系分类根节点“全部”，回填本空间所有对象、关系；
 * 每个对象：建属性分类根节点“全部”，回填其所有属性。</p>
 *
 * <p>所有写操作使用 mainTransactionManager，整体处于一个事务中（迁移型操作，要么全成功要么全回滚）。
 * 根节点采用“存在则复用、不存在才创建”的写法，保证接口可重复执行（幂等）。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OntologyCategoryInitServiceImpl implements OntologyCategoryInitService {

    private static final String ROOT_NAME = "全部";

    private final OntologySpaceService ontologySpaceService;
    private final OntologyCategoryService ontologyCategoryService;
    private final OntologyLinkCategoryService ontologyLinkCategoryService;
    private final PropertyCategoryService propertyCategoryService;
    private final OntologyMetaService ontologyMetaService;
    private final OntologyLinkGroupService ontologyLinkGroupService;
    private final OntologyPropertyService ontologyPropertyService;

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public Map<String, Integer> initAllCategoryRoots() {
        int spaceCount = 0;
        int metaUpdated = 0;
        int linkUpdated = 0;
        int propertyUpdated = 0;

        List<OntologySpace> spaces = ontologySpaceService.list();
        for (OntologySpace space : spaces) {
            Integer spaceId = space.getId();

            // 1. 本体对象分类树：建“全部”根节点
            Integer objRootId = getOrCreateOntologyCategoryRoot(spaceId);
            // 2. 关系分类树：建“全部”根节点
            Integer linkRootId = getOrCreateLinkCategoryRoot(spaceId);

            // 3. 本空间下所有对象的 categoryId 回填为对象分类根节点
            List<OntologyMeta> metasWithNullCategory = ontologyMetaService.list(
                    new LambdaQueryWrapper<OntologyMeta>()
                            .eq(OntologyMeta::getOntologySpaceId, spaceId)
                            .isNull(OntologyMeta::getOntologyCategoryId));
            for (OntologyMeta meta : metasWithNullCategory) {
                meta.setOntologyCategoryId(objRootId);
            }
            if (!metasWithNullCategory.isEmpty()) {
                ontologyMetaService.updateBatchById(metasWithNullCategory);
                metaUpdated += metasWithNullCategory.size();
            }

            // 4. 本空间下所有关系的 categoryId 回填为关系分类根节点
            List<OntologyLinkGroup> linksWithNullCategory = ontologyLinkGroupService.list(
                    new LambdaQueryWrapper<OntologyLinkGroup>()
                            .eq(OntologyLinkGroup::getOntologySpaceId, spaceId)
                            .isNull(OntologyLinkGroup::getCategoryId));
            for (OntologyLinkGroup link : linksWithNullCategory) {
                link.setCategoryId(linkRootId);
            }
            if (!linksWithNullCategory.isEmpty()) {
                ontologyLinkGroupService.updateBatchById(linksWithNullCategory);
                linkUpdated += linksWithNullCategory.size();
            }

            // 5. 每个本体对象：建属性分类根节点，并将其属性的 propertyCategoryId 回填
            List<OntologyMeta> allMetas = ontologyMetaService.list(
                    new LambdaQueryWrapper<OntologyMeta>()
                            .eq(OntologyMeta::getOntologySpaceId, spaceId));
            for (OntologyMeta meta : allMetas) {
                String uid = meta.getUniqueIdentifier();
                if (uid == null) {
                    continue;
                }
                Integer propRootId = getOrCreatePropertyCategoryRoot(uid);
                List<OntologyProperty> propsWithNullCategory = ontologyPropertyService.list(
                        new LambdaQueryWrapper<OntologyProperty>()
                                .eq(OntologyProperty::getOntologyUniqueIdentifier, uid)
                                .isNull(OntologyProperty::getPropertyCategoryId));
                for (OntologyProperty prop : propsWithNullCategory) {
                    prop.setPropertyCategoryId(propRootId);
                }
                if (!propsWithNullCategory.isEmpty()) {
                    ontologyPropertyService.updateBatchById(propsWithNullCategory);
                    propertyUpdated += propsWithNullCategory.size();
                }
            }

            spaceCount++;
            log.info("空间[{}] 分类树根节点初始化完成：对象分类回填 {} 个，关系分类回填 {} 个，属性分类回填 {} 个",
                    spaceId, metasWithNullCategory.size(), linksWithNullCategory.size(), propertyUpdated);
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("spaceCount", spaceCount);
        result.put("metaUpdated", metaUpdated);
        result.put("linkUpdated", linkUpdated);
        result.put("propertyUpdated", propertyUpdated);
        return result;
    }

    private Integer getOrCreateOntologyCategoryRoot(Integer spaceId) {
        List<OntologyCategory> existing = ontologyCategoryService.list(
                new LambdaQueryWrapper<OntologyCategory>()
                        .eq(OntologyCategory::getOntologySpaceId, spaceId)
                        .eq(OntologyCategory::getParentId, 0));
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }
        OntologyCategory root = OntologyCategory.builder()
                .ontologySpaceId(spaceId)
                .name(ROOT_NAME)
                .parentId(0)
                .path(ROOT_NAME)
                .build();
        ontologyCategoryService.save(root);
        return root.getId();
    }

    private Integer getOrCreateLinkCategoryRoot(Integer spaceId) {
        List<OntologyLinkCategory> existing = ontologyLinkCategoryService.list(
                new LambdaQueryWrapper<OntologyLinkCategory>()
                        .eq(OntologyLinkCategory::getOntologySpaceId, spaceId)
                        .eq(OntologyLinkCategory::getParentId, 0));
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }
        OntologyLinkCategory root = OntologyLinkCategory.builder()
                .ontologySpaceId(spaceId)
                .name(ROOT_NAME)
                .parentId(0)
                .path(ROOT_NAME)
                .build();
        ontologyLinkCategoryService.save(root);
        return root.getId();
    }

    private Integer getOrCreatePropertyCategoryRoot(String ontologyUniqueIdentifier) {
        List<PropertyCategory> existing = propertyCategoryService.list(
                new LambdaQueryWrapper<PropertyCategory>()
                        .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                        .eq(PropertyCategory::getParentId, 0));
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }
        PropertyCategory root = PropertyCategory.builder()
                .ontologyUniqueIdentifier(ontologyUniqueIdentifier)
                .name(ROOT_NAME)
                .parentId(0)
                .path(ROOT_NAME)
                .build();
        propertyCategoryService.save(root);
        return root.getId();
    }
}
