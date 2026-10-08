package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionVersion;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionStatusEnum;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

@Component
public class FunctionApiValidator implements ConstraintValidator<FunctionApiVerify, String> {

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private FunctionVersionMapper functionVersionMapper;

    @Override
    public boolean isValid(String api, ConstraintValidatorContext context) {
        if (StringUtils.isEmpty(api)) {
            return false;
        }
        var func = functionMapper.selectOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, api));
        return func != null && functionVersionMapper.selectCount(new LambdaQueryWrapper<FunctionVersion>()
                .eq(FunctionVersion::getFunctionId, func.getId())
                .eq(FunctionVersion::getVersionStatus, FunctionStatusEnum.PUBLISHED)) > 0;
    }
}
