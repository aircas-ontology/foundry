package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

@Component
public class OntologyDisplayNameValidator implements ConstraintValidator<OntologyDisplayNameVerify, String> {


    @Resource
    private OntologyMetaMapper metaMapper;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(StringUtils.isEmpty(value)){
            return false;
        }
        int count = metaMapper.selectByDisplayName(value);
        return count == 0;
    }
}
