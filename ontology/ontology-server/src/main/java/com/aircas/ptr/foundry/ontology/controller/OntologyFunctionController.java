package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;


@Api(tags = "函数")
@RestController
@RequestMapping("/function")
public class OntologyFunctionController {

    @Resource
    FunctionService functionService;

    @ApiOperation(value = "执行某个function")
    @PostMapping("/execute")
    public RestResult execute(@RequestBody @Valid FunctionExecuteParam executeParam) {
        try {
            return RestResult.ofData(functionService.handle(executeParam.getFunctionName(), false, executeParam.getObjectTypes(), executeParam.getParameters()));
        } catch (Exception e) {
            return RestResult.failed();
        }
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

    @ApiOperation(value = "得到本体下函数列表")
    @GetMapping("/list")
    public RestResult<List<FunctionInfoVO>> queryFunction(@RequestParam(required = false, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体uniqueIdentifier", required = false) String ontologyUniqueIdentifier) {
        return RestResult.success();
    }

    @ApiOperation(value = "创建函数")
    @PostMapping
    public RestResult createFunction(@RequestBody @Valid FunctionCreateParam param) {
        return RestResult.success();
    }

    @ApiOperation(value = "更新函数")
    @PutMapping
    public RestResult updateFunctionMetadata(@RequestBody FunctionCreateParam param) {
        return RestResult.success();
    }


    @ApiOperation(value = "根据函数id获取函数数据")
    @GetMapping("/query")
    public RestResult<FunctionInfoVO> getFunctionById(@RequestParam(required = true, name = "functionId") @ApiParam(value = "函数id", required = true) String functionId) {
        return RestResult.success();
    }


    @ApiOperation(value = "根据id删除函数")
    @DeleteMapping("/delete/{functionId}")
    public RestResult deleteById(@PathVariable(required = true, name = "functionId") Long functionId) {
        return RestResult.ofData(functionService.deleteById(functionId));
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
