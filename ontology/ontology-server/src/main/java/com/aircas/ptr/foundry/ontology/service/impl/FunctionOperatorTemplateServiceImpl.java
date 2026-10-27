package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.param.FunctionOperatorTemplateParam;
import com.aircas.ptr.foundry.ontology.model.po.FunctionOperatorTemplate;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionOperatorTemplateMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionOperatorTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 函数算子代码模板服务实现：提供模板的增删改查。
 */
@Service
@RequiredArgsConstructor
public class FunctionOperatorTemplateServiceImpl implements FunctionOperatorTemplateService {

    private final FunctionOperatorTemplateMapper templateMapper;

    @Override
    public List<FunctionOperatorTemplate> listTemplates(String name, FunctionLanguageEnum language) {
        LambdaQueryWrapper<FunctionOperatorTemplate> wrapper = new LambdaQueryWrapper<FunctionOperatorTemplate>()
                .like(StringUtils.isNotEmpty(name), FunctionOperatorTemplate::getName, name)
                .eq(language != null, FunctionOperatorTemplate::getLanguage, language)
                .orderByAsc(FunctionOperatorTemplate::getId);
        return templateMapper.selectList(wrapper);
    }

    @Override
    public FunctionOperatorTemplate create(FunctionOperatorTemplateParam param) {
        checkNameUnique(param.getName(), null);
        FunctionOperatorTemplate template = FunctionOperatorTemplate.builder()
                .name(param.getName())
                .language(param.getLanguage())
                .description(param.getDescription())
                .code(param.getCode())
                .build();
        // ID 由 bigserial 生成，create_time / update_time 由 OnInsertUpdateHandler 填充，都不走请求参数
        templateMapper.insert(template);
        return template;
    }

    @Override
    public FunctionOperatorTemplate update(Long id, FunctionOperatorTemplateParam param) {
        checkExists(id);
        checkNameUnique(param.getName(), id);
        // updateById 会跳过 null 字段，清空「模板说明」就写不进去，所以四个可改字段显式 set；
        // update_time 这里手动填（wrapper 更新没有实体对象，MetaObjectHandler 不生效）
        templateMapper.update(null, new LambdaUpdateWrapper<FunctionOperatorTemplate>()
                .eq(FunctionOperatorTemplate::getId, id)
                .set(FunctionOperatorTemplate::getName, param.getName())
                .set(FunctionOperatorTemplate::getLanguage, param.getLanguage())
                .set(FunctionOperatorTemplate::getDescription, param.getDescription())
                .set(FunctionOperatorTemplate::getCode, param.getCode())
                .set(FunctionOperatorTemplate::getUpdateTime, new Date()));
        return templateMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        checkExists(id);
        templateMapper.deleteById(id);
    }

    private void checkExists(Long id) {
        PreconditionUtils.checkNotNull(templateMapper.selectById(id), "模板不存在：" + id, HttpStatus.NOT_FOUND);
    }

    private void checkNameUnique(String name, Long excludeId) {
        long duplicated = templateMapper.selectCount(new LambdaQueryWrapper<FunctionOperatorTemplate>()
                .eq(FunctionOperatorTemplate::getName, name)
                .ne(excludeId != null, FunctionOperatorTemplate::getId, excludeId));
        PreconditionUtils.checkArgument(duplicated == 0, "模板名称已存在：" + name, HttpStatus.BAD_REQUEST);
    }
}
