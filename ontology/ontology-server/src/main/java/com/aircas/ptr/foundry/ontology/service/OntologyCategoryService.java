package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyCategory;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyCategoryVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OntologyCategoryService extends IService<OntologyCategory> {

    void createCategory(OntologyCategoryCreateParam param);

    void updateCategory(OntologyCategoryUpdateParam param);

    void deleteCategory(OntologyCategoryDeleteParam param);

    OntologyCategoryVO getCategoryTree(Integer spaceId);
}
