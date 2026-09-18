package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.model.param.OntologyDataSourceColumnParam;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

@Component
public class PrimaryKeyValidator implements ConstraintValidator<PrimaryKeyVerify, List<OntologyDataSourceColumnParam>> {


    @Override
    public boolean isValid(List<OntologyDataSourceColumnParam> dataSource, ConstraintValidatorContext context) {
        if (CollectionUtils.isEmpty(dataSource)) {
            return true;
        }

        var primaryKeyExist = dataSource.stream().filter(v -> v.getIsPrimaryKey()).count();
        return primaryKeyExist == 1;
    }
}
