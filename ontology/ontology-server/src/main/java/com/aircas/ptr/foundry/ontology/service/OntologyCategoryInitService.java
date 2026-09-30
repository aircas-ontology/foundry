package com.aircas.ptr.foundry.ontology.service;

import java.util.Map;

/**
 * 本体/关系/属性分类体系根节点初始化。
 *
 * <p>用于历史数据迁移：当前库中没有对象分类树、关系分类树、属性分类树，
 * 因此 ontology_meta / ontology_link_group / ontology_property 都没有 categoryId。
 * 该服务为每个空间分别建立对象分类树与关系分类树的根节点（名为“全部”），
 * 并将本空间下所有对象、关系的 categoryId 回填为对应根节点；
 * 同时为每个本体对象建立属性分类树根节点，并将其属性的 propertyCategoryId 回填。</p>
 */
public interface OntologyCategoryInitService {

    /**
     * 初始化全部空间的对象/关系/属性分类树根节点并回填 categoryId。
     *
     * @return 处理统计：spaceCount / metaUpdated / linkUpdated / propertyUpdated
     */
    Map<String, Integer> initAllCategoryRoots();
}
