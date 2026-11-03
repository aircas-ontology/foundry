package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.param.FunctionOperatorTemplateParam;
import com.aircas.ptr.foundry.ontology.model.po.FunctionOperatorTemplate;
import com.aircas.ptr.foundry.ontology.service.FunctionOperatorTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import jakarta.validation.Valid;
import java.util.List;

/**
 * 函数算子代码模板接口：按脚本语言维护示例代码模板的增删改查。
 */
@Tag(name = "函数算子代码模板")
@RestController
@RequestMapping("/function/operator_template")
public class FunctionOperatorTemplateController {

    @Resource
    private FunctionOperatorTemplateService templateService;

    @Operation(summary = "查询模板列表（name 不传查全部，传了按名称模糊匹配；language 不传查全部语言）")
    @GetMapping("/list")
    public RestResult<List<FunctionOperatorTemplate>> listTemplates(
            @RequestParam(required = false) String name,
            @Parameter(description = "脚本语言：GROOVY/JAVA/PYTHON/TYPESCRIPT")
            @RequestParam(required = false) FunctionLanguageEnum language) {
        return RestResult.ofData(templateService.listTemplates(name, language));
    }

    @Operation(summary = "创建函数算子代码模板（ID、创建/更新时间由后台生成，请求体只传名称/语言/说明/代码）")
    @PostMapping
    public RestResult<FunctionOperatorTemplate> create(@RequestBody @Valid FunctionOperatorTemplateParam param) {
        return RestResult.ofData(templateService.create(param));
    }

    @Operation(summary = "更新函数算子代码模板（ID 走 query 参数，更新时间由后台生成）")
    @PutMapping
    public RestResult<FunctionOperatorTemplate> update(
            @Parameter(description = "模板 ID")
            @RequestParam Long id,
            @RequestBody @Valid FunctionOperatorTemplateParam param) {
        return RestResult.ofData(templateService.update(id, param));
    }

    @Operation(summary = "删除函数算子代码模板")
    @DeleteMapping
    public RestResult<Void> delete(@RequestParam Long id) {
        templateService.delete(id);
        return RestResult.success();
    }
}
