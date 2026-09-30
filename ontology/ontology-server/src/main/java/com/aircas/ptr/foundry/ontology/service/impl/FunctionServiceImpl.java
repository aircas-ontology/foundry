package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionExecuteResult;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionExecuteResultMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.aircas.ptr.foundry.ontology.service.ScriptSecurityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionServiceImpl extends ServiceImpl<FunctionMapper, Function> implements FunctionService {

    private final static String ROOT_PATH = "functions";

    @Resource
    private OntologyActionMapper ontologyActionMapper;

    @Resource
    private FunctionExecuteResultMapper executeResultMapper;

    @Resource
    private FunctionParamService functionParamService;

    @Resource
    private GroovyService groovyService;

    @Lazy
    @Resource
    private EntityServiceImpl entityService;

    @Resource
    private ScriptSecurityService scriptSecurityService;

    private ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public Map<String, String> mapDescriptionByApi(List<String> functionApis) {
        if (functionApis == null || functionApis.isEmpty()) {
            return new HashMap<>();
        }
        // function.api 上有唯一约束 uk_function_api；description 列可为 null，
        // 过滤空白描述，避免 Collectors.toMap 在 value 为 null 时抛 NPE
        return list(new LambdaQueryWrapper<Function>()
                        .select(Function::getApi, Function::getDescription)
                        .in(Function::getApi, functionApis))
                .stream()
                .filter(function -> StringUtils.isNotBlank(function.getDescription()))
                .collect(Collectors.toMap(Function::getApi, Function::getDescription));
    }


    @Override
    public FunctionDetailVO getFunctionDetailByApi(String api) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, api));
        PreconditionUtils.checkArgument(function != null, "函数api不存在：" + api, HttpStatus.BAD_REQUEST);
        var functionParams = functionParamService.list(
                new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
        var params = functionParams.stream().map(p ->
                        FunctionParameterVO.builder()
                                .paramId(p.getId())
                                .paramName(p.getParamName())
                                .category(p.getCategory())
                                .paramOrder(p.getParamOrder())
                                .paramSchema(p.getParamSchema())
                                .paramType(p.getParamType())
                                .description(p.getDescription())
                                .build())
                .collect(Collectors.toList());
        return FunctionDetailVO.builder()
                .code(function.getCode())
                .referenceName(function.getReferenceName())
                .params(params)
                .functionApi(function.getApi())
                .displayName(function.getDisplayName())
                .description(function.getDescription())
                .type(function.getType())
                .model(function.getModel())
                .build();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteByApi(String api) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, api));
        PreconditionUtils.checkArgument(function != null, "无效的函数api:" + api, HttpStatus.BAD_REQUEST);
        //判断函数是否关联了行为，被本体行为使用到则不删除
        var ontologyActions = ontologyActionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getFunctionApi, api));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(ontologyActions), "该函数已被行为关联" + api);
        //删除函数参数
        functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
        //删除函数记录
        removeById(function.getId());
        //失效编译缓存，释放旧 Class / ClassLoader
        groovyService.invalidateCompiledClass(api);
    }


    @Override
    @Transactional(value = "mainTransactionManager")
    public void createFunction(FunctionCreateParam param) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        PreconditionUtils.checkArgument(function == null, "函数api已存在:" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        // 自定义函数在入库前必须通过安全检测。
        // 保存动作本身就会编译脚本（createFunction -> insertBatchFuncParams -> parseGroovyCode），
        // 一旦放行恶意代码，编译期（@Grab 拉依赖）与运行期（命令执行）都可能被利用，故必须在编译之前拦截。
        if (FunctionTypeEnum.CUSTOMIZE.equals(param.getType()) && StringUtils.isNotBlank(param.getCode())) {
            assertCodeSecurity(param.getCode());
        }
        //函数插入
        var func = Function.builder()
                .api(param.getFunctionApi())
                .description(param.getDescription())
                .displayName(param.getDisplayName())
                .code(param.getCode())
                .type(param.getType())
                .model(param.getModel())
                .status(Status.ENABLE.getValue())
                .referenceName(param.getReferenceName())
                .build();
        save(func);
        //自定义函数需要解析函数参数
        if (param.getType().equals(FunctionTypeEnum.CUSTOMIZE)) {
            //解析函数参数，批量入库
            insertBatchFuncParams(func.getId(), param.getCode());
        }
        //todo 暂不考虑注册的外部函数
    }

    /**
     * 安全检测不通过直接抛异常（硬拦截）。
     * <p>
     * 提示中只带规则说明与行号，不回显脚本正文，避免大对象进入日志与响应
     * （CODING_CONVENTIONS §13）。完整明细可通过 /function/validate_code 预校验接口获取。
     */
    private void assertCodeSecurity(String code) {
        var scan = scriptSecurityService.validateCode(code);
        if (scan.isPassed()) {
            return;
        }
        String detail = scan.getViolations().stream()
                .limit(5)
                .map(v -> v.getMessage() + (v.getLineNumber() > 0 ? "（第 " + v.getLineNumber() + " 行）" : ""))
                .collect(Collectors.joining("；"));
        // 提交的代码不合法，属于入参校验失败，固定错误码便于前端识别（CODING_CONVENTIONS §6）
        throw new BusinessException("函数代码未通过安全检测：" + detail,
                ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateFunction(FunctionUpdateParam param) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        //delete function
        deleteByApi(param.getFunctionApi());
        //create function
        createFunction(FunctionCreateParam.builder()
                .functionApi(param.getFunctionApi())
                .code(param.getCode())
                .description(param.getDescription())
                .displayName(param.getDisplayName())
                .referenceName(param.getReferenceName())
                .type(function.getType())
                .model(function.getModel())
                .build());
    }

    @Override
    @SneakyThrows
    public FunctionExecuteResultVO getExecuteResult(String taskId) {
        var executeResult = executeResultMapper.selectOne(new LambdaQueryWrapper<FunctionExecuteResult>()
                .eq(FunctionExecuteResult::getTaskId, taskId));
        if (executeResult == null) {
            return null;
        }
        return FunctionExecuteResultVO.builder()
                .result(StringUtils.isEmpty(executeResult.getResult()) ? null : objectMapper.readValue(executeResult.getResult(), new TypeReference<FunctionResultVO>() {
                }))
                .taskId(executeResult.getTaskId())
                .actionApi(executeResult.getActionApi())
                .functionApi(executeResult.getFunctionApi())
                .functionParam(executeResult.getFunctionParam())
                .taskStatus(executeResult.getTaskStatus())
                .build();
    }

    @SneakyThrows
    @Override
    public void callback(FunctionResultVO result) {
        var executeResult = executeResultMapper.selectOne(new LambdaQueryWrapper<FunctionExecuteResult>()
                .eq(FunctionExecuteResult::getTaskStatus, TaskStatusEnum.PENDING)
                .eq(FunctionExecuteResult::getTaskId, result.getTaskId()));
        PreconditionUtils.checkNotNull(executeResult, "task id 不存在：" + result.getTaskId());
        if (StringUtils.isNotEmpty(executeResult.getActionApi()) && StringUtils.isNotEmpty(executeResult.getActionContextInfo())) {
            //todo 更新实体行为、关系、属性
            var contextInfoDTO = objectMapper.readValue(executeResult.getActionContextInfo(), new TypeReference<ActionContextInfoDTO>() {
            });
            entityService.updateEntityPropertyAndRelation(objectMapper.writeValueAsString(result), contextInfoDTO);
        }
        executeResult.setTaskStatus(TaskStatusEnum.COMPLETED)
                .setResult(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        executeResultMapper.updateById(executeResult);
    }


    @Override
    public Page<FunctionInfoVO> getFunctions(Integer pageNum, Integer pageSize) {
        var pageInfo = new Page<Function>(pageNum, pageSize);
        pageInfo.addOrder(OrderItem.desc("create_time"));
        var functionPage = page(pageInfo);
        var records = functionPage.getRecords().stream().<FunctionInfoVO>map(func ->
                FunctionInfoVO.builder()
                        .functionApi(func.getApi())
                        .displayName(func.getDisplayName())
                        .type(func.getType())
                        .description(func.getDescription())
                        .build()
        ).collect(Collectors.toList());

        return new Page<FunctionInfoVO>()
                .setRecords(records)
                .setTotal(functionPage.getTotal())
                .setCurrent(functionPage.getCurrent())
                .setSize(functionPage.getSize());
    }

    @Override
    @SneakyThrows
    public String executeFunction(FunctionExecuteParam param) {
        //查询函数
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        //查询输入参数
        var executeInputParams = functionParamService.list(
                new LambdaQueryWrapper<FunctionParamPO>()
                        .eq(FunctionParamPO::getFunctionId, function.getId())
                        .eq(FunctionParamPO::getCategory, FunctionParamCategoryEnum.INPUT)
                        .orderByAsc(FunctionParamPO::getParamOrder));
        //参数取值
        Map<String, Object> funParamMap = CollectionUtils.isEmpty(param.getParameters()) ?
                new HashMap<>() : param.getParameters().stream().collect(HashMap::new, (m, p) -> m.put(p.getParamName(), p.getParamValue()), HashMap::putAll);
        var resultJsonStr = groovyService.executeGroovy(param.getFunctionApi(), function.getCode(), funParamMap, executeInputParams);
        var result = objectMapper.readValue(resultJsonStr, new TypeReference<FunctionResultVO>() {
        });
        if (result != null && StringUtils.isNotEmpty(result.getTaskId())) {
            executeResultMapper.insert(FunctionExecuteResult.builder()
                    .taskId(result.getTaskId())
                    .functionApi(param.getFunctionApi())
                    .functionParam(objectMapper.writeValueAsString(param.getParameters()))
                    .taskStatus(TaskStatusEnum.PENDING)
                    .build());
        }
        return resultJsonStr;
    }



    /***
     * 解析groovy代码，获取参数列表及返回值，批量入库
     * @param functionId
     * @param code
     */
    private void insertBatchFuncParams(Long functionId, String code) {
        //获取groovy参数、返回值信息
        List<FunctionParamDTO> functionParam = groovyService.parseGroovyCode(code);

        if (CollectionUtils.isNotEmpty(functionParam)) {
            List<FunctionParamPO> params = functionParam.stream().map(p ->
                    FunctionParamPO.builder()
                            .functionId(functionId)
                            .paramName(p.getParamName())
                            .paramType(p.getParamType())
                            .category(p.getCategory())
                            .paramSchema(p.getParamSchema())
                            .paramOrder(p.getParamOrder())
                            .typeReferenceName(p.getReferenceType())
                            .createTime(new Date())
                            .updateTime(new Date())
                            .build()
            ).collect(Collectors.toList());
            functionParamService.saveBatch(params);
        }
    }



}
