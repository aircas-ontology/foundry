package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Objects;

@Component
public class ApiNameValidator implements ConstraintValidator<ApiNameVerify, String> {


    @Resource
    private OntologyMetaMapper metaMapper;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (StringUtils.isEmpty(value)) {
            return false;
        }
        var meta = metaMapper.selectByApi(value);
        return Objects.isNull(meta);
    }
}
