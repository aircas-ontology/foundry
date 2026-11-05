package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.po.PropertyCategory;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.PropertyCategoryMapper;
import com.aircas.ptr.foundry.ontology.service.PropertyCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PropertyCategoryServiceImpl extends ServiceImpl<PropertyCategoryMapper, PropertyCategory> implements PropertyCategoryService {

    @Override
    public List<PropertyCategory> getCategoryList(String ontologyUniqueIdentifier) {
        return list(new LambdaQueryWrapper<PropertyCategory>()
                .eq(PropertyCategory::getOntologyUniqueIdentifier, ontologyUniqueIdentifier)
                .orderByAsc(PropertyCategory::getId));
    }
}
