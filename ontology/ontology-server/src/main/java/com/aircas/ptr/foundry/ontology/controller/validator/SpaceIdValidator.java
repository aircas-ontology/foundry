package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import lombok.var;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Component
public class SpaceIdValidator implements ConstraintValidator<SpaceIdVerify, Integer> {

    @Resource
    private OntologySpaceMapper spaceMapper;

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        var space = spaceMapper.selectById(value);
        return space != null;
    }
}