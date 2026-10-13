package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
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

    @Override
    public boolean isValid(String api, ConstraintValidatorContext context) {
        if (StringUtils.isEmpty(api)) {
            return false;
        }
        // 版本管理后 api 不再唯一，存在任意版本即合法
        return functionMapper.exists(new LambdaQueryWrapper<Function>().eq(Function::getApi, api));
    }
}
