package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCallbackParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;


@Api(tags = "函数")
@RestController
@RequestMapping("/function")
public class OntologyFunctionController {

    @Resource
    private FunctionService functionService;


    @PostMapping("/execute")
    @ApiOperation(value = "函数执行")
    public RestResult<String> executeFunction(@RequestBody @Valid FunctionExecuteParam param) {
        return RestResult.ofData(functionService.executeFunction(param));
    }


    @ApiOperation(value = "查询函数列表")
    @GetMapping("/list")
    public RestResult<Page<FunctionInfoVO>> getFunctions(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                         @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return RestResult.ofData(functionService.getFunctions(pageNum, pageSize));
    }

    @ApiOperation(value = "创建函数")
    @PostMapping
    public RestResult createFunction(@RequestBody @Valid FunctionCreateParam param) {
        //todo 需要增加代码安全检测
        functionService.createFunction(param);
        return RestResult.success();
    }

    @ApiOperation(value = "更新函数")
    @PutMapping
    public RestResult updateFunction(@RequestBody FunctionUpdateParam param) {
        // 需要 1 校验函数有没有被本体行为使用到，否则不能修改 2 需要增加代码安全检测
        functionService.updateFunction(param);
        return RestResult.success();
    }


    @ApiOperation(value = "根据函数api获取函数详情")
    @GetMapping("/detail")
    public RestResult<FunctionDetailVO> getFunctionByApi(@RequestParam(required = true, name = "functionApi") @ApiParam(value = "函数api", required = true) String functionApi) {
        return RestResult.ofData(functionService.getFunctionDetailByApi(functionApi));
    }


    @ApiOperation(value = "根据functionApi删除函数")
    @DeleteMapping("/delete/{functionApi}")
    public RestResult deleteById(@PathVariable(required = true, name = "functionApi") String functionApi) {
        //需要校验函数有没有被本体行为使用到
        functionService.deleteByApi(functionApi);
        return RestResult.success();
    }


    @PostMapping("/callback")
    @ApiOperation(value = "异步函数执行结果回调")
    public RestResult functionCallback(@RequestBody @Valid FunctionCallbackParam param) {
        functionService.callback(param.getResult());
        return RestResult.success();
    }


    @GetMapping("/callback_result")
    @ApiOperation(value = "根据taskId查询异步函数执行结果")
    public RestResult<FunctionExecuteResultVO> getExecuteResult(@RequestParam(required = true, name = "taskId") @ApiParam(value = "任务id", required = true) String taskId) {
        return RestResult.ofData(functionService.getExecuteResult(taskId));
    }

}
