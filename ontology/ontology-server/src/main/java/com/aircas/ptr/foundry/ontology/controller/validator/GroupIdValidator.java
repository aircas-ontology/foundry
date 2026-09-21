package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.model.po.OntologyGroup;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyGroupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

@Component
public class GroupIdValidator implements ConstraintValidator<GroupIdVerify, String> {


    @Resource
    private OntologyGroupMapper groupMapper;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (StringUtils.isEmpty(value)) {
            return false;
        }
        var group = groupMapper.selectOne(new LambdaQueryWrapper<OntologyGroup>().eq(OntologyGroup::getGroupId, value));
        return group != null;
    }
}
