package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.composite.BasicQueryOperator;
import com.aircas.ptr.foundry.ontology.composite.CompositeChainRegistry;
import com.aircas.ptr.foundry.ontology.composite.CompositeFunctionCatalog;
import com.aircas.ptr.foundry.ontology.composite.CompositeFunctionInvoker;
import com.aircas.ptr.foundry.ontology.composite.ExternalApiOperator;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeValidationBO;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamRoleEnum;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryConfig;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryTestParam;
import com.aircas.ptr.foundry.ontology.model.param.CompositeConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeNode;
import com.aircas.ptr.foundry.ontology.model.param.CompositeParameter;
import com.aircas.ptr.foundry.ontology.model.param.CompositeValidateParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyGenericQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.ExternalApiConfig;
import com.aircas.ptr.foundry.ontology.model.param.FilterGroupParam;
import com.aircas.ptr.foundry.ontology.model.param.FilterNodeParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySelectPropertyParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyFilterParam;
import com.aircas.ptr.foundry.ontology.model.enums.FilterNodeTypeEnum;
import com.aircas.ptr.foundry.ontology.model.vo.BasicQueryResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyGenericQueryVO;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.FunctionExecuteResult;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionExecuteResultMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.ArrayList;
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
    private OntologyPropertyMapper propertyMapper;

    @Resource
    private CompositeChainRegistry compositeChainRegistry;

    /** 基础查询算子执行器：单算子测试与编排节点共用同一套语义 */
    @Resource
    private BasicQueryOperator basicQueryOperator;

    /** 外部API算子执行器：注册时用于推导端口，执行时用于发起 HTTP 调用 */
    @Resource
    private ExternalApiOperator externalApiOperator;

    /** 按函数类型分发执行的算子调用器（脚本/基础查询/外部API） */
    @Resource
    private CompositeFunctionInvoker compositeFunctionInvoker;

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

        // BASIC_QUERY 类型：解析 queryConfig，并根据 targetProperty 动态推导每个参数的角色
        BasicQueryConfig queryConfig = null;
        if (function.getType() == FunctionTypeEnum.BASIC_QUERY && StringUtils.isNotEmpty(function.getCode())) {
            try {
                queryConfig = objectMapper.readValue(function.getCode(), BasicQueryConfig.class);
                // 动态设置 paramRole：有聚合目标时匹配的为 AGGREGATION，其余均为 FILTER
                String targetVar = queryConfig != null ? queryConfig.getTargetProperty() : null;
                params.forEach(vo ->
                        vo.setParamRole(StringUtils.isNotEmpty(targetVar) && vo.getParamName().equals(targetVar)
                                ? FunctionParamRoleEnum.AGGREGATION
                                : FunctionParamRoleEnum.FILTER));
            } catch (JsonProcessingException e) {
                log.warn("解析基础查询算子配置失败: {}", api, e);
            }
        }

        // COMPOSITE 类型：解析编排画布配置，前端据此回显工作流
        CompositeConfig compositeConfig = null;
        if (function.getType() == FunctionTypeEnum.COMPOSITE && StringUtils.isNotEmpty(function.getCode())) {
            try {
                compositeConfig = objectMapper.readValue(function.getCode(), CompositeConfig.class);
            } catch (JsonProcessingException e) {
                log.warn("解析函数编排配置失败: {}", api, e);
            }
        }

        // EXTERNAL 类型：解析接口定义，前端据此回显请求/响应映射
        ExternalApiConfig apiConfig = null;
        if (function.getType() == FunctionTypeEnum.EXTERNAL && StringUtils.isNotEmpty(function.getCode())) {
            try {
                apiConfig = objectMapper.readValue(function.getCode(), ExternalApiConfig.class);
            } catch (JsonProcessingException e) {
                log.warn("解析外部API算子配置失败: {}", api, e);
            }
        }

        return FunctionDetailVO.builder()
                .code(function.getCode())
                .referenceName(function.getReferenceName())
                .params(params)
                .queryConfig(queryConfig)
                .compositeConfig(compositeConfig)
                .apiConfig(apiConfig)
                .functionApi(function.getApi())
                .displayName(function.getDisplayName())
                .description(function.getDescription())
                .type(function.getType())
                .model(function.getModel())
                .ontologySpaceId(function.getOntologySpaceId())
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
        //卸载编排执行链，避免删除后仍命中旧的 LiteFlow 链定义
        compositeChainRegistry.evict(api);
    }


    @Override
    @Transactional(value = "mainTransactionManager")
    public void createFunction(FunctionCreateParam param) {
        // 自动填充 model：未传时根据 type 推断
        FunctionModelEnum model = param.getModel();
        if (model == null) {
            model = (param.getType() == FunctionTypeEnum.BASIC_QUERY) 
                    ? FunctionModelEnum.BASIC 
                    : FunctionModelEnum.OTHER;
        }
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        PreconditionUtils.checkArgument(function == null, "函数api已存在:" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        //函数插入
        var func = Function.builder()
                .api(param.getFunctionApi())
                .description(param.getDescription())
                .displayName(param.getDisplayName())
                .code(param.getCode())
                .type(param.getType())
                .model(model)
                .status(Status.ENABLE.getValue())
                .referenceName(param.getReferenceName())
                .ontologySpaceId(param.getOntologySpaceId())
                .build();
        save(func);
        //自定义函数需要解析函数参数
        if (param.getType().equals(FunctionTypeEnum.CUSTOMIZE)) {
            //解析函数参数，批量入库
            insertBatchFuncParams(func.getId(), param.getCode());
        }
        //基础查询算子：序列化 queryConfig 到 code，提取变量存入 function_param
        if (param.getType().equals(FunctionTypeEnum.BASIC_QUERY)) {
            createBasicQueryParams(func.getId(), param.getQueryConfig());
        }
        //函数编排：校验画布配置，序列化到 code，并把画布出入参写入 function_param
        if (param.getType().equals(FunctionTypeEnum.COMPOSITE)) {
            createCompositeParams(func.getId(), param.getFunctionApi(), param.getCompositeConfig());
        }
        //外部API算子：序列化 apiConfig 到 code，并按请求/响应映射生成出入参，使其可接入编排工作流
        if (param.getType().equals(FunctionTypeEnum.EXTERNAL)) {
            createExternalParams(func.getId(), param.getApiConfig());
        }
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateFunction(FunctionUpdateParam param) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        PreconditionUtils.checkArgument(function != null, "函数api不存在：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        // 更新编排函数时，若请求未重新提交画布配置，则沿用库中已存的配置，避免画布被清空
        CompositeConfig compositeConfig = param.getCompositeConfig();
        if (compositeConfig == null && function.getType() == FunctionTypeEnum.COMPOSITE) {
            compositeConfig = parseCompositeConfig(function.getCode());
        }
        // 更新外部API算子时同理，未重新提交接口定义则沿用库中配置
        ExternalApiConfig apiConfig = param.getApiConfig();
        if (apiConfig == null && function.getType() == FunctionTypeEnum.EXTERNAL) {
            apiConfig = parseExternalConfig(function.getCode(), param.getFunctionApi());
        }
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
                .model(param.getModel())
                .ontologySpaceId(function.getOntologySpaceId())
                .queryConfig(param.getQueryConfig())
                .compositeConfig(compositeConfig)
                .apiConfig(apiConfig)
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
    public Page<FunctionInfoVO> getFunctions(Integer ontologySpaceId, String displayName,
                                              FunctionTypeEnum type,
                                              String startDate, String endDate,
                                              Integer pageNum, Integer pageSize) {
        var pageInfo = new Page<Function>(pageNum, pageSize);
        pageInfo.addOrder(OrderItem.desc("create_time"));
        var wrapper = new LambdaQueryWrapper<Function>();
        if (ontologySpaceId != null) {
            wrapper.eq(Function::getOntologySpaceId, ontologySpaceId);
        }
        if (StringUtils.isNotEmpty(displayName)) {
            wrapper.like(Function::getDisplayName, displayName);
        }
        if (type != null) {
            wrapper.eq(Function::getType, type);
        }
        if (StringUtils.isNotEmpty(startDate)) {
            wrapper.ge(Function::getCreateTime, startDate);
        }
        if (StringUtils.isNotEmpty(endDate)) {
            wrapper.le(Function::getCreateTime, endDate);
        }
        var functionPage = page(pageInfo, wrapper);
        var records = functionPage.getRecords().stream().<FunctionInfoVO>map(func ->
                FunctionInfoVO.builder()
                        .functionApi(func.getApi())
                        .displayName(func.getDisplayName())
                        .type(func.getType())
                        .description(func.getDescription())
                        .ontologySpaceId(func.getOntologySpaceId())
                        .updateTime(func.getUpdateTime())
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
        PreconditionUtils.checkArgument(function != null, "函数不存在：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        //查询输入参数
        var executeInputParams = functionParamService.list(
                new LambdaQueryWrapper<FunctionParamPO>()
                        .eq(FunctionParamPO::getFunctionId, function.getId())
                        .eq(FunctionParamPO::getCategory, FunctionParamCategoryEnum.INPUT)
                        .orderByAsc(FunctionParamPO::getParamOrder));
        //参数取值
        Map<String, Object> funParamMap = CollectionUtils.isEmpty(param.getParameters()) ?
                new HashMap<>() : param.getParameters().stream().collect(HashMap::new, (m, p) -> m.put(p.getParamName(), p.getParamValue()), HashMap::putAll);
        //函数编排：走 LiteFlow 执行链，不复用 groovy 脚本执行逻辑
        if (function.getType() == FunctionTypeEnum.COMPOSITE) {
            return executeCompositeFunction(function, funParamMap);
        }
        //基础查询算子/外部API算子：与编排节点共用同一套执行语义
        if (function.getType() == FunctionTypeEnum.BASIC_QUERY
                || function.getType() == FunctionTypeEnum.EXTERNAL) {
            return executeOperatorFunction(function, funParamMap, executeInputParams);
        }
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

    /**
     * 执行单算子（基础查询/外部API）：复用编排节点调用器，返回值统一包装成 FunctionResultVO 结构，
     * 保证与脚本算子、函数编排的执行返回格式一致。
     */
    private String executeOperatorFunction(Function function, Map<String, Object> funParamMap,
                                           List<FunctionParamPO> inputParams) throws JsonProcessingException {
        Object raw = compositeFunctionInvoker.invoke(function, null, funParamMap, inputParams);
        Map<String, Object> data = raw instanceof Map<?, ?> map ? toStringKeyMap(map) : Map.of("result", raw);
        var result = FunctionResultVO.builder()
                .startTime(new Date())
                .data(data)
                .description("函数算子执行结果")
                .build();
        return objectMapper.writeValueAsString(result);
    }

    /** 算子返回值可能带非字符串键，统一转成 JSON 可序列化的字符串键结构 */
    private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }



    // ==================== 基础查询算子 ====================

    /**
     * 基础查询算子创建时：序列化 queryConfig → code，提取变量名 → function_param。
     */
    @SneakyThrows
    private void createBasicQueryParams(Long functionId, BasicQueryConfig queryConfig) {
        PreconditionUtils.checkArgument(queryConfig != null, "基础查询算子必须提供 queryConfig", HttpStatus.BAD_REQUEST);
        // 聚合操作：targetProperty 必须由前端传入（作为变量名），执行时通过 variableBindings 映射到实际属性 apiName
        if (queryConfig.getAggFunc() != null && StringUtils.isEmpty(queryConfig.getTargetProperty())) {
            PreconditionUtils.checkArgument(false, "聚合函数必须提供 targetProperty（聚合目标字段）", HttpStatus.BAD_REQUEST);
        }

        // 更新 code 字段为 queryConfig JSON
        var func = getById(functionId);
        func.setCode(objectMapper.writeValueAsString(queryConfig));
        updateById(func);

        // 收集变量名 → 数据类型映射
        Map<String, String> varTypeMap = new HashMap<>();
        String targetVar = queryConfig.getTargetProperty();
        if (StringUtils.isNotEmpty(targetVar)) {
            varTypeMap.put(targetVar, "STRING");
        }
        collectFilterVariables(queryConfig.getFilters(), varTypeMap);

        List<FunctionParamPO> params = new ArrayList<>();
        int order = 1;
        // 本体标识作为第一个入参：作为编排画布节点时，必须能从上游拿到查询的目标本体
        params.add(FunctionParamPO.builder()
                .functionId(functionId)
                .paramName(BasicQueryOperator.PARAM_ONTOLOGY_IDENTIFIER)
                .paramType(FunctionParamTypeEnum.STRING)
                .category(FunctionParamCategoryEnum.INPUT)
                .paramOrder(order++)
                .description("查询目标本体标识")
                .createTime(new Date())
                .updateTime(new Date())
                .build());
        for (var entry : varTypeMap.entrySet()) {
            String varName = entry.getKey();
            // 根据变量角色设置描述：聚合目标 vs 过滤条件
            String description = varName.equals(targetVar) ? "聚合目标" : "过滤条件";
            params.add(FunctionParamPO.builder()
                    .functionId(functionId)
                    .paramName(varName)
                    .paramType(mapDataType(entry.getValue()))
                    .category(FunctionParamCategoryEnum.INPUT)
                    .paramOrder(order++)
                    .description(description)
                    .createTime(new Date())
                    .updateTime(new Date())
                    .build());
        }
        params.addAll(buildBasicQueryOutputParams(functionId, queryConfig));
        if (!params.isEmpty()) {
            functionParamService.saveBatch(params);
        }
    }

    /**
     * 基础查询算子的输出参数：与 {@link BasicQueryOperator#runAsNode} 的端口名一一对应，
     * 编排画布节点的端口完全由 function_param 推导，因此这里必须落库，否则下游无法连线取值。
     */
    private List<FunctionParamPO> buildBasicQueryOutputParams(Long functionId, BasicQueryConfig queryConfig) {
        List<FunctionParamPO> outputs = new ArrayList<>();
        int order = 1;
        if (queryConfig.getAggFunc() != null) {
            // 聚合模式：result/value 为聚合值，另附聚合函数、别名与命中的实际属性名
            outputs.add(basicQueryOutput(functionId, BasicQueryOperator.PORT_RESULT, FunctionParamTypeEnum.DOUBLE,
                    "聚合结果", order++));
            outputs.add(basicQueryOutput(functionId, BasicQueryOperator.PORT_VALUE, FunctionParamTypeEnum.DOUBLE,
                    "聚合值", order++));
            outputs.add(basicQueryOutput(functionId, "aggFunc", FunctionParamTypeEnum.STRING,
                    "聚合函数", order++));
            outputs.add(basicQueryOutput(functionId, "alias", FunctionParamTypeEnum.STRING,
                    "聚合别名", order++));
            outputs.add(basicQueryOutput(functionId, "targetProperty", FunctionParamTypeEnum.STRING,
                    "聚合目标属性", order));
        } else {
            // 查询模式：result/records 为分页记录，total 为总数
            outputs.add(basicQueryOutput(functionId, BasicQueryOperator.PORT_RESULT, FunctionParamTypeEnum.LIST,
                    "查询结果记录", order++));
            outputs.add(basicQueryOutput(functionId, BasicQueryOperator.PORT_RECORDS, FunctionParamTypeEnum.LIST,
                    "分页记录", order++));
            outputs.add(basicQueryOutput(functionId, BasicQueryOperator.PORT_TOTAL, FunctionParamTypeEnum.LONG,
                    "记录总数", order));
        }
        return outputs;
    }

    private FunctionParamPO basicQueryOutput(Long functionId, String name, FunctionParamTypeEnum paramType,
                                             String description, int paramOrder) {
        return FunctionParamPO.builder()
                .functionId(functionId)
                .paramName(name)
                .paramType(paramType)
                .category(FunctionParamCategoryEnum.OUTPUT)
                .paramOrder(paramOrder)
                .description(description)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    // ==================== 外部API函数算子 ====================

    /**
     * 外部API算子创建时：序列化 apiConfig → code，并生成出入参 function_param。
     * <p>编排画布节点的端口完全由 function_param 推导，因此这里落库后，
     * 外部API算子既能通过 {@code /function/execute} 单独调用，也能作为画布节点接入编排工作流。</p>
     */
    @SneakyThrows
    private void createExternalParams(Long functionId, ExternalApiConfig apiConfig) {
        PreconditionUtils.checkArgument(apiConfig != null, "外部API算子必须提供 apiConfig", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(apiConfig.getEndpoint()),
                "外部API算子必须提供接口地址 endpoint", HttpStatus.BAD_REQUEST);

        var func = getById(functionId);
        func.setCode(objectMapper.writeValueAsString(apiConfig));
        updateById(func);

        List<FunctionParamPO> params = new ArrayList<>();
        int order = 1;
        for (ExternalApiConfig.PortDefinition port : externalInputPorts(apiConfig)) {
            params.add(externalParam(functionId, port, FunctionParamCategoryEnum.INPUT, order++));
        }
        int outputOrder = 1;
        for (ExternalApiConfig.PortDefinition port : externalOutputPorts(apiConfig)) {
            params.add(externalParam(functionId, port, FunctionParamCategoryEnum.OUTPUT, outputOrder++));
        }
        if (!params.isEmpty()) {
            functionParamService.saveBatch(params);
        }
    }

    /** 入参端口：显式声明优先，否则从请求映射的 parameterId 推导（跳过 {@code literal:} 字面量） */
    private List<ExternalApiConfig.PortDefinition> externalInputPorts(ExternalApiConfig apiConfig) {
        if (!apiConfig.inputsOrEmpty().isEmpty()) {
            return apiConfig.inputsOrEmpty();
        }
        Map<String, ExternalApiConfig.PortDefinition> ports = new java.util.LinkedHashMap<>();
        for (ExternalApiConfig.RequestMapping mapping : apiConfig.requestMappingsOrEmpty()) {
            String name = mapping.getParameterId();
            if (StringUtils.isBlank(name) || name.startsWith(ExternalApiConfig.LITERAL_PREFIX)) {
                continue;
            }
            ports.putIfAbsent(name.trim(), ExternalApiConfig.PortDefinition.builder().name(name.trim()).build());
        }
        return new ArrayList<>(ports.values());
    }

    /** 出参端口：显式声明优先，否则从响应映射的 parameterId 推导；两者都为空时兜底 result/status */
    private List<ExternalApiConfig.PortDefinition> externalOutputPorts(ExternalApiConfig apiConfig) {
        if (!apiConfig.outputsOrEmpty().isEmpty()) {
            return apiConfig.outputsOrEmpty();
        }
        Map<String, ExternalApiConfig.PortDefinition> ports = new java.util.LinkedHashMap<>();
        for (ExternalApiConfig.ResponseMapping mapping : apiConfig.responseMappingsOrEmpty()) {
            String name = mapping.getParameterId();
            if (StringUtils.isBlank(name)) {
                continue;
            }
            ports.putIfAbsent(name.trim(), ExternalApiConfig.PortDefinition.builder().name(name.trim()).build());
        }
        if (ports.isEmpty()) {
            ports.put(ExternalApiOperator.PORT_RESULT, ExternalApiConfig.PortDefinition.builder()
                    .name(ExternalApiOperator.PORT_RESULT).type("object").description("响应报文").build());
            ports.put(ExternalApiOperator.PORT_STATUS, ExternalApiConfig.PortDefinition.builder()
                    .name(ExternalApiOperator.PORT_STATUS).type("number").description("HTTP 状态码").build());
        }
        return new ArrayList<>(ports.values());
    }

    private FunctionParamPO externalParam(Long functionId, ExternalApiConfig.PortDefinition port,
                                          FunctionParamCategoryEnum category, int paramOrder) {
        return FunctionParamPO.builder()
                .functionId(functionId)
                .paramName(port.getName())
                .paramType(CompositeFunctionCatalog.toParamType(port.getType()))
                .category(category)
                .paramOrder(paramOrder)
                .description(port.getDescription())
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    /** 解析 function.code 中的外部API接口定义 */
    private ExternalApiConfig parseExternalConfig(String code, String functionApi) {
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        try {
            return objectMapper.readValue(code, ExternalApiConfig.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("外部API算子定义解析失败：" + functionApi + "，" + e.getMessage(),
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 递归收集过滤树中的变量名及其数据类型。
     */
    private void collectFilterVariables(FilterGroupParam group, Map<String, String> result) {
        if (group == null || CollectionUtils.isEmpty(group.getChildren())) {
            return;
        }
        for (var node : group.getChildren()) {
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                if (StringUtils.isNotEmpty(node.getFilter().getPropertyApiName())) {
                    result.put(node.getFilter().getPropertyApiName(), node.getFilter().getDataType());
                }
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                collectFilterVariables(node.getGroup(), result);
            }
        }
    }

    /**
     * 将前端传入的 dataType（STRING/NUMBER/BOOLEAN）映射为 FunctionParamTypeEnum。
     */
    private FunctionParamTypeEnum mapDataType(String dataType) {
        if (StringUtils.isEmpty(dataType)) {
            return FunctionParamTypeEnum.OBJECT;
        }
        switch (dataType.toUpperCase()) {
            case "STRING": return FunctionParamTypeEnum.STRING;
            case "NUMBER": return FunctionParamTypeEnum.DOUBLE;
            case "BOOLEAN": return FunctionParamTypeEnum.BOOL;
            default: return FunctionParamTypeEnum.OBJECT;
        }
    }

    /**
     * 基础查询算子测试执行：委托给 {@link BasicQueryOperator}，与编排节点共用同一套语义。
     * 聚合模式返回 BasicQueryResultVO，query 模式返回分页结果。
     */
    @Override
    public Object testBasicQuery(BasicQueryTestParam param) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        PreconditionUtils.checkArgument(function != null, "函数不存在：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(function.getType() == FunctionTypeEnum.BASIC_QUERY,
                "该函数不是基础查询算子", HttpStatus.BAD_REQUEST);
        return basicQueryOperator.run(basicQueryOperator.parseConfig(function.getCode(), function.getApi()),
                param.getOntologyIdentifier(), param.getVariableBindings(), param.getPageNum(), param.getPageSize());
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

    /**
     * 统一函数测试入口：根据函数类型分发到不同测试逻辑。
     */
    @SneakyThrows
    @Override
    public Object testFunction(FunctionTestParam param) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        PreconditionUtils.checkArgument(function != null, "函数不存在：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);

        switch (function.getType()) {
            case BASIC_QUERY:
                // 转换为 BasicQueryTestParam 并调用 testBasicQuery
                var basicParam = BasicQueryTestParam.builder()
                        .functionApi(param.getFunctionApi())
                        .ontologyIdentifier(param.getOntologyIdentifier())
                        .variableBindings(param.getVariableBindings())
                        .pageNum(param.getPageNum())
                        .pageSize(param.getPageSize())
                        .build();
                return testBasicQuery(basicParam);

            case CUSTOMIZE:
            case EXTERNAL:
            case COMPOSITE:
                // 复用 executeFunction 逻辑：脚本走 groovy，外部API走接口定义，编排走 LiteFlow 链
                var executeParam = FunctionExecuteParam.builder()
                        .functionApi(param.getFunctionApi())
                        .parameters(param.getParameters())
                        .build();
                return executeFunction(executeParam);

            default:
                throw new BusinessException("不支持的函数类型：" + function.getType());
        }
    }

    // ==================== 函数编排（COMPOSITE） ====================

    @Override
    public CompositeValidationBO validateComposite(CompositeValidateParam param) {
        PreconditionUtils.checkArgument(param != null, "校验请求不能为空", HttpStatus.BAD_REQUEST);
        var config = param.getCompositeConfig();
        var functionApi = param.getFunctionApi();
        if (config == null) {
            PreconditionUtils.checkArgument(StringUtils.isNotEmpty(functionApi), "必须提供 compositeConfig 或 functionApi", HttpStatus.BAD_REQUEST);
            config = loadStoredCompositeConfig(functionApi);
        }
        checkNoSelfReference(config, functionApi);
        return compositeChainRegistry.validate(config);
    }

    @Override
    public CompositePlanBO compileComposite(String functionApi) {
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(functionApi), "functionApi不能为空", HttpStatus.BAD_REQUEST);
        var function = loadCompositeFunction(functionApi);
        var config = parseCompositeConfig(function.getCode());
        PreconditionUtils.checkArgument(config != null, "函数编排定义为空：" + functionApi, HttpStatus.BAD_REQUEST);
        return compositeChainRegistry.compile(CompositeChainRegistry.chainNameOf(functionApi), config);
    }

    /**
     * 执行函数编排：把画布配置编译为 LiteFlow 链并驱动执行，返回值沿用 FunctionResultVO 结构。
     */
    private String executeCompositeFunction(Function function, Map<String, Object> funParamMap) throws JsonProcessingException {
        CompositeConfig config = parseCompositeConfig(function.getCode());
        PreconditionUtils.checkArgument(config != null, "函数编排定义为空：" + function.getApi(), HttpStatus.BAD_REQUEST);
        Map<String, Object> outputs = compositeChainRegistry.execute(function.getApi(), config, funParamMap);
        var result = FunctionResultVO.builder()
                .startTime(new Date())
                .data(outputs)
                .description("函数编排执行结果")
                .build();
        return objectMapper.writeValueAsString(result);
    }

    /**
     * 解析 function.code 中的编排定义，解析失败时给出明确文案而不是裸异常。
     */
    private CompositeConfig parseCompositeConfig(String code) {
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        try {
            return objectMapper.readValue(code, CompositeConfig.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("函数编排定义解析失败：" + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 编排函数创建时：先校验画布合法性，再把定义序列化落库，并生成对外的 function_param。
     */
    @SneakyThrows
    private void createCompositeParams(Long functionId, String functionApi, CompositeConfig compositeConfig) {
        PreconditionUtils.checkArgument(compositeConfig != null, "函数编排必须提供 compositeConfig", HttpStatus.BAD_REQUEST);
        CompositeValidationBO validation = compositeChainRegistry.validate(compositeConfig);
        PreconditionUtils.checkArgument(validation.isValid(),
                "函数编排校验不通过：" + String.join("；", validation.getErrors()), HttpStatus.BAD_REQUEST);
        if (CollectionUtils.isNotEmpty(validation.getWarnings())) {
            log.warn("函数编排[{}]校验告警：{}", functionApi, String.join("；", validation.getWarnings()));
        }
        checkNoSelfReference(compositeConfig, functionApi);

        var func = getById(functionId);
        func.setCode(objectMapper.writeValueAsString(compositeConfig));
        updateById(func);

        List<FunctionParamPO> params = new ArrayList<>();
        int order = 1;
        for (CompositeParameter parameter : compositeConfig.getInputParameters()) {
            params.add(buildCompositeParam(functionId, parameter, FunctionParamCategoryEnum.INPUT, order++));
        }
        int outputOrder = 1;
        for (CompositeParameter parameter : compositeConfig.getOutputParameters()) {
            params.add(buildCompositeParam(functionId, parameter, FunctionParamCategoryEnum.OUTPUT, outputOrder++));
        }
        if (!params.isEmpty()) {
            functionParamService.saveBatch(params);
        }
    }

    /**
     * 画布对外参数 → function_param 记录，画布类型（string/number/...）转换为后端参数类型。
     */
    private FunctionParamPO buildCompositeParam(Long functionId, CompositeParameter parameter,
                                                FunctionParamCategoryEnum category, int paramOrder) {
        return FunctionParamPO.builder()
                .functionId(functionId)
                .paramName(parameter.getName())
                .paramType(CompositeFunctionCatalog.toParamType(parameter.getType()))
                .category(category)
                .paramOrder(paramOrder)
                .description(parameter.getDescription())
                .createTime(new Date())
                .updateTime(new Date())
                .build();
    }

    private CompositeConfig loadStoredCompositeConfig(String functionApi) {
        var function = loadCompositeFunction(functionApi);
        CompositeConfig config = parseCompositeConfig(function.getCode());
        PreconditionUtils.checkArgument(config != null, "函数编排定义为空：" + functionApi, HttpStatus.BAD_REQUEST);
        return config;
    }

    private Function loadCompositeFunction(String functionApi) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, functionApi));
        PreconditionUtils.checkArgument(function != null, "函数api不存在：" + functionApi, HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(function.getType() == FunctionTypeEnum.COMPOSITE,
                "该函数不是函数编排类型：" + functionApi, HttpStatus.BAD_REQUEST);
        return function;
    }

    /**
     * 编排不能引用自身，否则 LiteFlow 链会自嵌套导致无限递归。
     */
    private void checkNoSelfReference(CompositeConfig config, String functionApi) {
        if (StringUtils.isEmpty(functionApi) || config == null) {
            return;
        }
        for (CompositeNode node : config.getNodes()) {
            boolean selfReference = node != null && StringUtils.isNotEmpty(node.getFunctionId())
                    && node.getFunctionId().trim().equals(functionApi.trim());
            PreconditionUtils.checkArgument(!selfReference, "函数编排不能引用自身：" + functionApi, HttpStatus.BAD_REQUEST);
        }
    }
}
