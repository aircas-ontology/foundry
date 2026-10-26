package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.po.FunctionRateLimitTemplate;
import com.aircas.ptr.foundry.ontology.service.FunctionRateLimitTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 限流配置模板接口：模板的增删改查与启停管理。
 */
@Tag(name = "限流配置模板")
@RestController
@RequestMapping("/function/rate_limit_template")
public class FunctionRateLimitTemplateController {

    @Resource
    private FunctionRateLimitTemplateService templateService;

    @Operation(summary = "列出全部启用的限流配置模板（创建函数时选用）")
    @GetMapping("/list")
    public RestResult<List<FunctionRateLimitTemplate>> list() {
        return RestResult.ofData(templateService.listEnabled());
    }

    @Operation(summary = "创建限流配置模板")
    @PostMapping
    public RestResult<FunctionRateLimitTemplate> create(@RequestBody FunctionRateLimitTemplate template) {
        return RestResult.ofData(templateService.create(template));
    }

    @Operation(summary = "更新限流配置模板")
    @PutMapping
    public RestResult<FunctionRateLimitTemplate> update(@RequestBody FunctionRateLimitTemplate template) {
        return RestResult.ofData(templateService.update(template.getId(), template));
    }

    @Operation(summary = "删除限流配置模板")
    @DeleteMapping
    public RestResult<Void> delete(@RequestParam Long id) {
        templateService.delete(id);
        return RestResult.success();
    }

    @Operation(summary = "启用限流配置模板")
    @PutMapping("/enable")
    public RestResult<Void> enable(@RequestParam Long id) {
        templateService.enable(id);
        return RestResult.success();
    }

    @Operation(summary = "停用限流配置模板")
    @PutMapping("/disable")
    public RestResult<Void> disable(@RequestParam Long id) {
        templateService.disable(id);
        return RestResult.success();
    }
}
