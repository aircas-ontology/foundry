package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCallbackParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import java.util.List;


@Tag(name = "函数算子管理")
@RestController
@RequestMapping("/function")
public class OntologyFunctionController {

    @Resource
    private FunctionService functionService;


    @PostMapping("/execute")
    @Operation(summary = "函数执行")
    public RestResult<String> executeFunction(@RequestBody @Valid FunctionExecuteParam param) {
        return RestResult.ofData(functionService.executeFunction(param));
    }


    @Operation(summary = "查询函数列表")
    @GetMapping("/list")
    public RestResult<Page<FunctionInfoVO>> getFunctions(
            @RequestParam(required = false) @Parameter(description = "空间id") Integer ontologySpaceId,
            @RequestParam(required = false) @Parameter(description = "函数名称（模糊搜索）") String displayName,
            @RequestParam(required = false) @Parameter(description = "函数类型") FunctionTypeEnum type,
            @RequestParam(required = false) @Parameter(description = "发布状态：0 未发布，1 已发布") Integer publishStatus,
            @RequestParam(required = false) @Parameter(description = "创建开始日期，格式 yyyy-MM-dd") String startDate,
            @RequestParam(required = false) @Parameter(description = "创建结束日期，格式 yyyy-MM-dd") String endDate,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return RestResult.ofData(functionService.getFunctions(ontologySpaceId, displayName, type, publishStatus, startDate, endDate, pageNum, pageSize));
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
    public RestResult<FunctionDetailVO> getFunctionByApi(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") String functionApi,
                                                         @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") String version) {
        return RestResult.ofData(functionService.getFunctionDetailByApi(functionApi, version));
    }


    @Operation(summary = "删除函数")
    @DeleteMapping("/delete")
    public RestResult deleteById(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") String functionApi,
                                 @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") String version) {
        //需要校验函数有没有被本体行为使用到
        functionService.deleteByApi(functionApi, version);
        return RestResult.success();
    }


    @Operation(summary = "发布函数")
    @PostMapping("/publish")
    public RestResult publishFunction(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") String functionApi,
                                      @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") String version) {
        functionService.publishFunction(functionApi, version);
        return RestResult.success();
    }


    @Operation(summary = "下线函数")
    @PostMapping("/unpublish")
    public RestResult unpublishFunction(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") String functionApi,
                                        @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") String version) {
        functionService.unpublishFunction(functionApi, version);
        return RestResult.success();
    }


    @PostMapping("/callback")
    @Operation(summary = "异步函数执行结果回调")
    public RestResult functionCallback(@RequestBody @Valid FunctionCallbackParam param) {
        functionService.callback(param.getResult());
        return RestResult.success();
    }


    @GetMapping("/callback_result")
    @Operation(summary = "根据taskId查询异步函数执行结果")
    public RestResult<FunctionExecuteResultVO> getExecuteResult(@RequestParam(required = true, name = "taskId") @Parameter(description = "任务id") String taskId) {
        return RestResult.ofData(functionService.getExecuteResult(taskId));
    }

    @PostMapping("/test")
    @Operation(summary = "函数测试")
    public RestResult<Object> testFunction(@RequestBody @Valid FunctionTestParam param) {
        return RestResult.ofData(functionService.testFunction(param));
    }

}
