package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.model.param.PropertyDatasourceParam;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Component
public class DatasourceValidator implements ConstraintValidator<DatasourceVerify, PropertyDatasourceParam> {


    @Resource
    private TableMetadataMapper tableMetadataMapper;

    @Override
    public boolean isValid(PropertyDatasourceParam value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        var schemaName = value.getSchemaName();
        var dsId = value.getDatasourceId();
        var columnName = value.getDatasourceColumnName();
        if (StringUtils.isEmpty(dsId)
                && StringUtils.isEmpty(schemaName)
                && StringUtils.isEmpty(columnName)) {
            return true;
        }
        if (StringUtils.isNotEmpty(dsId)
                && StringUtils.isNotEmpty(schemaName)
                && StringUtils.isNotEmpty(columnName)) {
            return tableMetadataMapper.isColumnExist(schemaName, dsId, columnName);
        } else {
            return false;
        }

    }
}
