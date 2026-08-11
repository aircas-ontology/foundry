package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceIdParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.var;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Component
public class SpaceIdValidator implements ConstraintValidator<SpaceIdVerify, OntologySpaceIdParam> {

    @Resource
    private OntologySpaceMapper spaceMapper;

    @Override
    public boolean isValid(OntologySpaceIdParam param, ConstraintValidatorContext context) {
        //兼容旧版本，如果spaceId为空，默认使用public空间
        if (param.getSpaceId() == null) {
            var space = spaceMapper.selectOne(new LambdaQueryWrapper<OntologySpace>()
                    .eq(OntologySpace::getApiName, "public"));
            if (space == null) {
                return false;
            }
            param.setSpaceId(space.getId());
            return true;
        }
        var space = spaceMapper.selectById(param.getSpaceId());
        return space != null;
    }
}