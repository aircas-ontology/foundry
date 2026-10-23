package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeValidationBO;
import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionPublishResponse;
import com.aircas.ptr.foundry.ontology.model.dto.scan.FunctionScanResponse;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Resource
    private ScriptParamParseService scriptParamParseService;


    @PostMapping("/execute")
    @Operation(summary = "函数执行", description = "正式执行入口，仅允许执行 publish=1 的目标版本；未发布版本返回 HTTP 400")
    @ApiResponse(responseCode = "400", description = "目标版本不存在或尚未发布")
    public RestResult<String> executeFunction(@RequestBody @Valid FunctionExecuteParam param) {
        return RestResult.ofData(functionService.executeFunction(param));
    }


    @Operation(summary = "查询函数列表", description = "列表项返回 scanStatus 和仅含 summary 的 scanResult，不返回 findings")
    @GetMapping("/list")
    public RestResult<Page<FunctionInfoVO>> getFunctions(FunctionListQueryParam param) {
        return RestResult.ofData(functionService.getFunctions(param));
    }

    @Operation(summary = "创建函数", description = "脚本算子同步执行安全扫描；HIGH 风险可保存为未发布草稿，响应返回扫描摘要")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功，返回扫描状态和摘要"),
            @ApiResponse(responseCode = "422", description = "脚本解析或本地校验失败，不落库"),
            @ApiResponse(responseCode = "502", description = "安全扫描报告或服务异常"),
            @ApiResponse(responseCode = "503", description = "安全扫描服务不可用"),
            @ApiResponse(responseCode = "504", description = "安全扫描超时")
    })
    @PostMapping
    public RestResult<FunctionScanResponse> createFunction(@RequestBody @Valid FunctionCreateParam param) {
        return RestResult.ofData(FunctionScanResponse.from(functionService.createFunction(param)));
    }

    @Operation(summary = "更新函数", description = "仅修改未发布版本；同步安全扫描并使用 update_time 乐观锁，响应返回扫描摘要")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功，返回扫描状态和摘要"),
            @ApiResponse(responseCode = "409", description = "扫描期间函数版本发生变化"),
            @ApiResponse(responseCode = "422", description = "脚本解析或本地校验失败，不落库"),
            @ApiResponse(responseCode = "502", description = "安全扫描报告或服务异常"),
            @ApiResponse(responseCode = "503", description = "安全扫描服务不可用"),
            @ApiResponse(responseCode = "504", description = "安全扫描超时")
    })
    @PutMapping
    public RestResult<FunctionScanResponse> updateFunction(@RequestBody @Valid FunctionUpdateParam param) {
        return RestResult.ofData(FunctionScanResponse.from(functionService.updateFunction(param)));
    }


    @Operation(summary = "查询函数详情", description = "返回 scanStatus、固定 11 个顶层字段的完整 scanResult 及全部 findings")
    @GetMapping("/detail")
    public RestResult<FunctionDetailVO> getFunctionByApi(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") String functionApi,
                                                         @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") String version) {
        return RestResult.ofData(functionService.getFunctionDetailByApi(functionApi, version));
    }


    @Operation(summary = "根据函数api查询版本号列表")
    @GetMapping("/versions")
    public RestResult<List<String>> getFunctionVersions(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") String functionApi) {
        return RestResult.ofData(functionService.listVersionsByApi(functionApi));
    }


    @Operation(summary = "删除函数")
    @DeleteMapping("/delete")
    public RestResult deleteById(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") String functionApi,
                                 @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") String version) {
        //需要校验函数有没有被本体行为使用到
        functionService.deleteByApi(functionApi, version);
        return RestResult.success();
    }


    @Operation(summary = "发布函数", description = "发布前校验扫描状态；旧跳过脚本按当前开关补扫。HTTP 200 时仍须检查 data.published")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "发布成功或因扫描拒绝发布；调用方必须检查 data.published"),
            @ApiResponse(responseCode = "409", description = "补扫期间函数版本发生变化"),
            @ApiResponse(responseCode = "422", description = "补扫发现脚本解析或本地校验失败"),
            @ApiResponse(responseCode = "502", description = "安全扫描报告或服务异常"),
            @ApiResponse(responseCode = "503", description = "安全扫描服务不可用"),
            @ApiResponse(responseCode = "504", description = "安全扫描超时")
    })
    @PostMapping("/publish")
    public RestResult<FunctionPublishResponse> publishFunction(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") String functionApi,
                                                               @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") String version) {
        FunctionPublishResponse data = FunctionPublishResponse.from(
                functionService.publishFunction(functionApi, version));
        RestResult<FunctionPublishResponse> response = RestResult.ofData(data);
        if (!data.published()) {
            response.setMessage("安全扫描未通过，函数未发布");
        }
        return response;
    }


    @Operation(summary = "下线函数")
    @PostMapping("/unpublish")
    public RestResult unpublishFunction(@RequestParam(required = true, name = "functionApi") @Parameter(description = "函数api") String functionApi,
                                        @RequestParam(required = true, name = "version") @Parameter(description = "版本号（必填，格式 x.y.z）") String version) {
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
    public RestResult<FunctionExecuteResultVO> getExecuteResult(@RequestParam(required = true, name = "taskId") @Parameter(description = "任务id") String taskId) {
        return RestResult.ofData(functionService.getExecuteResult(taskId));
    }

    @PostMapping("/test")
    @Operation(summary = "函数测试（支持已存库 / 草稿两种模式）")
    public RestResult<Object> testFunction(@RequestBody @Valid FunctionTestParam param) {
        return RestResult.ofData(functionService.testFunction(param));
    }

    @PostMapping("/composite/validate")
    @Operation(summary = "校验函数编排定义（不落库）")
    public RestResult<CompositeValidationBO> validateComposite(@RequestBody CompositeValidateParam param) {
        return RestResult.ofData(functionService.validateComposite(param));
    }

    @GetMapping("/composite/el")
    @Operation(summary = "编译已落库的函数编排定义，返回 LiteFlow 执行链 EL")
    public RestResult<CompositePlanBO> compileComposite(
            @RequestParam(required = true, name = "functionApi") @Parameter(description = "编排函数api") String functionApi) {
        return RestResult.ofData(functionService.compileComposite(functionApi));
    }

}
