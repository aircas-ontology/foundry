package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

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
