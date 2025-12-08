package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Component
@RequiredArgsConstructor
public class OntologyIdValidator implements ConstraintValidator<OntologyIdVerify, String> {


    private final OntologyMetaMapper metaMapper;


    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (StringUtils.isEmpty(value)) {
            return false;
        }
        var meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, value).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        return meta != null;
    }
}
