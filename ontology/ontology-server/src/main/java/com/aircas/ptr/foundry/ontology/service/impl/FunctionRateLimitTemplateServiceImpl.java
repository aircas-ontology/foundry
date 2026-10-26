package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.po.FunctionRateLimitTemplate;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionRateLimitTemplateMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionRateLimitTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 限流配置模板服务实现：提供模板的增删改查与启停管理。
 */
@Service
@RequiredArgsConstructor
public class FunctionRateLimitTemplateServiceImpl implements FunctionRateLimitTemplateService {

    private final FunctionRateLimitTemplateMapper templateMapper;

    @Override
    public List<FunctionRateLimitTemplate> listEnabled() {
        return templateMapper.selectList(
                new LambdaQueryWrapper<FunctionRateLimitTemplate>()
                        .eq(FunctionRateLimitTemplate::getEnabled, 1)
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
        // 默认取第一个启用的模板（ID 最小的）
        return templateMapper.selectOne(
                new LambdaQueryWrapper<FunctionRateLimitTemplate>()
                        .eq(FunctionRateLimitTemplate::getEnabled, 1)
                        .orderByAsc(FunctionRateLimitTemplate::getId)
                        .last("LIMIT 1"));
    }

    @Override
    public FunctionRateLimitTemplate create(FunctionRateLimitTemplate template) {
        // 校验必填字段
        PreconditionUtils.checkArgument(template.getName() != null && !template.getName().isBlank(),
                "模板名称不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getTimeoutMs() != null && template.getTimeoutMs() > 0,
                "超时时间必须大于 0", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getRetryCount() != null && template.getRetryCount() >= 0,
                "重试次数不能为负数", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getRetryIntervalMs() != null && template.getRetryIntervalMs() >= 0,
                "重试间隔不能为负数", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getTpsLimit() != null && template.getTpsLimit() > 0,
                "TPS 限制必须大于 0", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getMaxConcurrency() != null && template.getMaxConcurrency() > 0,
                "最大并发数必须大于 0", HttpStatus.BAD_REQUEST);

        // 默认启用
        if (template.getEnabled() == null) {
            template.setEnabled(1);
        }

        template.setCreateTime(new Date());
        template.setUpdateTime(new Date());
        templateMapper.insert(template);
        return template;
    }

    @Override
    public FunctionRateLimitTemplate update(Long id, FunctionRateLimitTemplate template) {
        FunctionRateLimitTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("模板不存在：" + id, HttpStatus.NOT_FOUND);
        }

        // 校验必填字段
        PreconditionUtils.checkArgument(template.getName() != null && !template.getName().isBlank(),
                "模板名称不能为空", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getTimeoutMs() != null && template.getTimeoutMs() > 0,
                "超时时间必须大于 0", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getRetryCount() != null && template.getRetryCount() >= 0,
                "重试次数不能为负数", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getRetryIntervalMs() != null && template.getRetryIntervalMs() >= 0,
                "重试间隔不能为负数", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getTpsLimit() != null && template.getTpsLimit() > 0,
                "TPS 限制必须大于 0", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(template.getMaxConcurrency() != null && template.getMaxConcurrency() > 0,
                "最大并发数必须大于 0", HttpStatus.BAD_REQUEST);

        template.setId(id);
        template.setUpdateTime(new Date());
        templateMapper.updateById(template);
        return templateMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        FunctionRateLimitTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在：" + id, HttpStatus.NOT_FOUND);
        }

        // 检查是否为默认模板（ID 最小的启用模板）
        FunctionRateLimitTemplate defaultTemplate = getDefaultTemplate();
        if (defaultTemplate != null && defaultTemplate.getId().equals(id)) {
            throw new BusinessException("不能删除默认模板，请先设置其他模板为默认", HttpStatus.BAD_REQUEST);
        }

        templateMapper.deleteById(id);
    }

    @Override
    public void enable(Long id) {
        FunctionRateLimitTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在：" + id, HttpStatus.NOT_FOUND);
        }

        templateMapper.update(null,
                new LambdaUpdateWrapper<FunctionRateLimitTemplate>()
                        .eq(FunctionRateLimitTemplate::getId, id)
                        .set(FunctionRateLimitTemplate::getEnabled, 1)
                        .set(FunctionRateLimitTemplate::getUpdateTime, new Date()));
    }

    @Override
    public void disable(Long id) {
        FunctionRateLimitTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在：" + id, HttpStatus.NOT_FOUND);
        }

        // 检查是否为最后一个启用的模板
        long enabledCount = templateMapper.selectCount(
                new LambdaQueryWrapper<FunctionRateLimitTemplate>()
                        .eq(FunctionRateLimitTemplate::getEnabled, 1));
        if (enabledCount <= 1) {
            throw new BusinessException("不能停用最后一个启用的模板，系统至少需要一个可用模板", HttpStatus.BAD_REQUEST);
        }

        // 检查是否为默认模板
        FunctionRateLimitTemplate defaultTemplate = getDefaultTemplate();
        if (defaultTemplate != null && defaultTemplate.getId().equals(id)) {
            throw new BusinessException("不能停用默认模板，请先设置其他模板为默认", HttpStatus.BAD_REQUEST);
        }

        templateMapper.update(null,
                new LambdaUpdateWrapper<FunctionRateLimitTemplate>()
                        .eq(FunctionRateLimitTemplate::getId, id)
                        .set(FunctionRateLimitTemplate::getEnabled, 0)
                        .set(FunctionRateLimitTemplate::getUpdateTime, new Date()));
    }
}
