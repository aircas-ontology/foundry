package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.po.FunctionRateLimitTemplate;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionRateLimitTemplateMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionRateLimitTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 限流配置模板服务实现：模板数据由 DDL 预置，此处只提供查询。
 */
@Service
@RequiredArgsConstructor
public class FunctionRateLimitTemplateServiceImpl implements FunctionRateLimitTemplateService {

    private final FunctionRateLimitTemplateMapper templateMapper;

    @Override
    public List<FunctionRateLimitTemplate> listAll() {
        return templateMapper.selectList(
                new LambdaQueryWrapper<FunctionRateLimitTemplate>()
                        .orderByAsc(FunctionRateLimitTemplate::getId));
    }

    @Override
    public FunctionRateLimitTemplate getById(Long id) {
        if (id == null) {
            return getDefaultTemplate();
        }
        return templateMapper.selectById(id);
    }

    @Override
    public FunctionRateLimitTemplate getDefaultTemplate() {
        // 默认取第一个模板（ID 最小的，即"宽松"）
        return templateMapper.selectOne(
                new LambdaQueryWrapper<FunctionRateLimitTemplate>()
                        .orderByAsc(FunctionRateLimitTemplate::getId)
                        .last("LIMIT 1"));
    }
}
