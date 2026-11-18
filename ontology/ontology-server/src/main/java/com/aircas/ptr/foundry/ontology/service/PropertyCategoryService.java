package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.PropertyCategory;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

public interface PropertyCategoryService extends IService<PropertyCategory> {

    List<PropertyCategory> getCategoryList(String ontologyUniqueIdentifier);
}
