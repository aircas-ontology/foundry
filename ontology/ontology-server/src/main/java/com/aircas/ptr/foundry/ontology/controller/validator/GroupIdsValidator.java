package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.model.po.OntologyGroup;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.Set;

@Component
public class GroupIdsValidator implements ConstraintValidator<GroupIdsVerify, Set<String>> {


    @Resource
    private OntologyGroupMapper groupMapper;

    @Override
    public boolean isValid(Set<String> value, ConstraintValidatorContext context) {
        if(CollectionUtils.isEmpty(value)){
            return false;
        }
        List<OntologyGroup> list = groupMapper.selectList(new LambdaQueryWrapper<OntologyGroup>().in(OntologyGroup::getGroupId,value));
        return CollectionUtils.isNotEmpty(list) && list.size() == value.size();
    }
}
