package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.FunctionRateLimitTemplate;

import java.util.List;

/**
 * 限流配置模板服务：管理预置模板，供创建函数时选用。
 */
public interface FunctionRateLimitTemplateService {

    /** 列出全部模板（创建函数时下拉选择用） */
    List<FunctionRateLimitTemplate> listAll();

    /** 按 ID 获取模板（创建函数时复制配置快照用） */
    FunctionRateLimitTemplate getById(Long id);

    /** 获取默认模板（未指定模板时使用） */
    FunctionRateLimitTemplate getDefaultTemplate();
}
