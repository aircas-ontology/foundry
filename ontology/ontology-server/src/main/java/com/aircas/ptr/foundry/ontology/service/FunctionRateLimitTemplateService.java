package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.FunctionRateLimitTemplate;

import java.util.List;

/**
 * 限流配置模板服务：管理预置模板，供创建函数时选用。
 */
public interface FunctionRateLimitTemplateService {

    /** 列出全部启用的模板（创建函数时下拉选择用） */
    List<FunctionRateLimitTemplate> listEnabled();

    /** 按 ID 获取模板（创建函数时复制配置快照用） */
    FunctionRateLimitTemplate getById(Long id);

    /** 获取默认模板（未指定模板时使用） */
    FunctionRateLimitTemplate getDefaultTemplate();

    /** 创建模板 */
    FunctionRateLimitTemplate create(FunctionRateLimitTemplate template);

    /** 更新模板 */
    FunctionRateLimitTemplate update(Long id, FunctionRateLimitTemplate template);

    /** 删除模板 */
    void delete(Long id);

    /** 启用模板 */
    void enable(Long id);

    /** 停用模板 */
    void disable(Long id);
}
