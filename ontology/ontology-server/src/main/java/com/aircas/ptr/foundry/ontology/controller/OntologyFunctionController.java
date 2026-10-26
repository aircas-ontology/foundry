package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeValidationBO;
import com.aircas.ptr.foundry.ontology.model.param.CompositeValidateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCallbackParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionListQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ScriptParseParam;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.ScriptParseResultVO;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.ScriptParamParseService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;


@Tag(name = "函数算子管理")
@RestController
@RequestMapping("/function")
public class OntologyFunctionController {

    @Resource
    private FunctionService functionService;

    @Resource
    private ScriptParamParseService scriptParamParseService;


    @PostMapping("/execute")
    @Operation(summary = "函数执行")
    public RestResult<String> executeFunction(@RequestBody @Valid FunctionExecuteParam param) {
        return RestResult.ofData(functionService.executeFunction(param));
    }


    @Operation(summary = "查询函数列表")
    @GetMapping("/list")
    public RestResult<Page<FunctionInfoVO>> getFunctions(@Valid FunctionListQueryParam param) {
        return RestResult.ofData(functionService.getFunctions(param));
    }

    @Operation(summary = "创建函数")
    @PostMapping
    public RestResult createFunction(@RequestBody @Valid FunctionCreateParam param) {
        //todo 需要增加代码安全检测
        functionService.createFunction(param);
        return RestResult.success();
    }

    @Operation(summary = "更新函数")
    @PutMapping
    public RestResult updateFunction(@RequestBody @Valid FunctionUpdateParam param) {
        //todo 需要增加代码安全检测
        functionService.updateFunction(param);
        return RestResult.success();
    }


    @Operation(summary = "查询函数详情")
    @GetMapping("/detail")
    public RestResult<FunctionDetailVO> getFunctionByApi(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") @NotBlank(message = "functionApi is empty") String functionApi,
                                                         @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") @NotBlank(message = "version is empty") String version) {
        return RestResult.ofData(functionService.getFunctionDetailByApi(functionApi, version));
    }


    @Operation(summary = "根据函数api查询版本号列表")
    @GetMapping("/versions")
    public RestResult<List<String>> getFunctionVersions(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") @NotBlank(message = "functionApi is empty") String functionApi) {
        return RestResult.ofData(functionService.listVersionsByApi(functionApi));
    }


    @Operation(summary = "删除函数")
    @DeleteMapping("/delete")
    public RestResult deleteById(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") @NotBlank(message = "functionApi is empty") String functionApi,
                                 @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") @NotBlank(message = "version is empty") String version) {
        //需要校验函数有没有被本体行为使用到
        functionService.deleteByApi(functionApi, version);
        return RestResult.success();
    }


    @Operation(summary = "发布函数")
    @PostMapping("/publish")
    public RestResult publishFunction(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") @NotBlank(message = "functionApi is empty") String functionApi,
                                      @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") @NotBlank(message = "version is empty") String version) {
        functionService.publishFunction(functionApi, version);
        return RestResult.success();
    }


    @Operation(summary = "下线函数")
    @PostMapping("/unpublish")
    public RestResult unpublishFunction(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") @NotBlank(message = "functionApi is empty") String functionApi,
                                        @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") @NotBlank(message = "version is empty") String version) {
        functionService.unpublishFunction(functionApi, version);
        return RestResult.success();
    }


    @PostMapping("/parse_params")
    @Operation(summary = "解析脚本输入/输出参数（后端 AST 解析，Java/Groovy 进程内、Python/TS 走 runner）")
    public RestResult<ScriptParseResultVO> parseParams(@RequestBody @Valid ScriptParseParam param) {
        return RestResult.ofData(scriptParamParseService.parseParams(param));
    }

    @PostMapping("/callback")
    @Operation(summary = "异步函数执行结果回调")
    public RestResult functionCallback(@RequestBody @Valid FunctionCallbackParam param) {
        functionService.callback(param.getResult());
        return RestResult.success();
    }


    @GetMapping("/callback_result")
    @Operation(summary = "根据taskId查询异步函数执行结果")
    public RestResult<FunctionExecuteResultVO> getExecuteResult(@RequestParam(required = true, name = "taskId") @Parameter(description = "任务id") @NotBlank(message = "taskId is empty") String taskId) {
        return RestResult.ofData(functionService.getExecuteResult(taskId));
    }

    @PostMapping("/test")
    @Operation(summary = "函数测试（支持已存库 / 草稿两种模式）")
    public RestResult<Object> testFunction(@RequestBody @Valid FunctionTestParam param) {
        return RestResult.ofData(functionService.testFunction(param));
    }

    @PostMapping("/composite/validate")
    @Operation(summary = "校验函数编排定义（不落库）")
    public RestResult<CompositeValidationBO> validateComposite(@RequestBody @Valid CompositeValidateParam param) {
        return RestResult.ofData(functionService.validateComposite(param));
    }

    @GetMapping("/composite/el")
    @Operation(summary = "编译已落库的函数编排定义，返回 LiteFlow 执行链 EL")
    public RestResult<CompositePlanBO> compileComposite(
            @RequestParam(required = true, name = "functionApi") @Parameter(description = "编排函数api") @NotBlank(message = "functionApi is empty") String functionApi) {
        return RestResult.ofData(functionService.compileComposite(functionApi));
    }

}
