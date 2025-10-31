package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
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
    FunctionService functionService;


    @PostMapping("/execute")
    @ApiOperation(value = "函数执行")
    public RestResult executeFunction(@RequestBody @Valid FunctionExecuteParam param) {
        return RestResult.ofData(null);
    }


//    @ApiOperation(value = "得到函数参数")
//    @GetMapping("/parameter")
//    public RestResult queryParameter(@RequestParam(required = true,name = "functionName") @ApiParam(name = "functionName",value = "函数名称") String functionName) {
//        try {
//            //todo 修改成从function表中获取参数
//            return RestResult.ofData(functionService.getParameters(functionName));
//        } catch (BaseException e) {
//            return RestResult.failed();
//        }
//    }

    @ApiOperation(value = "查询函数列表")
    @GetMapping("/list")
    public RestResult<Page<FunctionInfoVO>> getFunctions(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                         @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return RestResult.success();
    }

    @ApiOperation(value = "创建函数")
    @PostMapping
    public RestResult createFunction(@RequestBody @Valid FunctionCreateParam param) {
        return RestResult.success();
    }

    @ApiOperation(value = "更新函数")
    @PutMapping
    public RestResult updateFunction(@RequestBody FunctionUpdateParam param) {
        return RestResult.success();
    }


    @ApiOperation(value = "根据函数id获取函数详情")
    @GetMapping("/detail")
    public RestResult<FunctionDetailVO> getFunctionByApi(@RequestParam(required = true, name = "functionApi") @ApiParam(value = "函数api", required = true) String functionApi) {
        return RestResult.success();
    }


    @ApiOperation(value = "根据id删除函数")
    @DeleteMapping("/delete/{functionApi}")
    public RestResult deleteById(@PathVariable(required = true, name = "functionApi") String functionApi) {
        return RestResult.ofData(null);
    }

//    @ApiOperation(value = "读取函数列表")
//    @GetMapping("/meta/list")
//    public DataResult<List<FunctionVO>> functionList() {
//        return DataResult.ofData(functionService.functionMetadataList());
//    }

//    @ApiOperation(value = "保存代码")
//    @PostMapping("/code")
//    public DataResult<Boolean> saveCode(@RequestBody HashMap map) {
//
//        String functionName = (String) map.getOrDefault("functionName", null);
//        if (functionName == null) {
//            return DataResult.ofData(false);
//        }
//        String code = (String) map.getOrDefault("code", null);
//        if (code == null) {
//            return DataResult.ofData(false);
//        }
//        Boolean isPreview = (Boolean) map.getOrDefault("isPreview", true);
//        return DataResult.ofData(functionService.write(functionName, code, isPreview));
//    }

//    @ApiOperation(value = "获取函数代码")
//    @GetMapping("/code/{api}")
//    public DataResult<String> getCode(@PathVariable String api) {
//
//        return DataResult.ofData(functionService.get(api,false));
//    }


}
