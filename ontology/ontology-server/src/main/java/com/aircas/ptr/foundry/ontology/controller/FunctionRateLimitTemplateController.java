package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.po.FunctionRateLimitTemplate;
import com.aircas.ptr.foundry.ontology.service.FunctionRateLimitTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 限流配置模板接口：供前端创建函数时下拉选择。
 */
@Tag(name = "限流配置模板")
@RestController
@RequestMapping("/function/rate_limit_template")
public class FunctionRateLimitTemplateController {

    @Resource
    private FunctionRateLimitTemplateService templateService;

    @Operation(summary = "列出全部限流配置模板（创建函数时选用）")
    @GetMapping("/list")
    public RestResult<List<FunctionRateLimitTemplate>> list() {
        return RestResult.ofData(templateService.listAll());
    }
}
