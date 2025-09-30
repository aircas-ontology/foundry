package com.aircas.ptr.foundry.ontology.controller.validator;

import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class OntologyApiNameValidator implements ConstraintValidator<OntologyApiNameVerify, String> {


    private final Pattern pattern = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$");

    @Resource
    private OntologyMetaMapper metaMapper;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (StringUtils.isEmpty(value)) {
            return false;
        }
        if (!pattern.matcher(value).matches()) {
            return false;
        }
        var meta = metaMapper.selectOne(new QueryWrapper<OntologyMeta>().eq("api_name", value));
        return Objects.isNull(meta);
    }
}
