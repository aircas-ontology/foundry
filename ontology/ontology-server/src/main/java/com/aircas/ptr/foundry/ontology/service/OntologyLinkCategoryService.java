package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkCategoryVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OntologyLinkCategoryService extends IService<OntologyLinkCategory> {

    void createCategory(OntologyLinkCategoryCreateParam param);

    void updateCategory(OntologyLinkCategoryUpdateParam param);

    void deleteCategory(OntologyLinkCategoryDeleteParam param);

    OntologyLinkCategoryVO getCategoryTree(Integer spaceId);
}
