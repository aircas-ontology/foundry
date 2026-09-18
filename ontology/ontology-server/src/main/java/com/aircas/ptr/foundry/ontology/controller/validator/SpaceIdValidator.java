package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

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