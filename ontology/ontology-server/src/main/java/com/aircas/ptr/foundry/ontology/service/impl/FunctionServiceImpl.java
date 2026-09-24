package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamRoleEnum;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryConfig;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryTestParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyGenericQueryParam;
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

    /**
     * 基础查询算子聚合目标字段的默认占位符名（前端未传时后端自动生成）
     */
    private final static String DEFAULT_TARGET_PLACEHOLDER = "target";

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

    private ObjectMapper objectMapper = new ObjectMapper();


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

        return FunctionDetailVO.builder()
                .code(function.getCode())
                .referenceName(function.getReferenceName())
                .params(params)
                .queryConfig(queryConfig)
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
        //todo 暂不考虑注册的外部函数
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
                .model(param.getModel())
                .ontologySpaceId(function.getOntologySpaceId())
                .queryConfig(param.getQueryConfig())
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



    // ==================== 基础查询算子 ====================

    /**
     * 基础查询算子创建时：序列化 queryConfig → code，提取变量名 → function_param。
     */
    @SneakyThrows
    private void createBasicQueryParams(Long functionId, BasicQueryConfig queryConfig) {
        PreconditionUtils.checkArgument(queryConfig != null, "基础查询算子必须提供 queryConfig", HttpStatus.BAD_REQUEST);
        // 聚合操作：targetProperty 仅为占位符（创建时不选实际字段，执行时才绑定），前端未传则后端自动生成
        if (queryConfig.getAggFunc() != null && StringUtils.isEmpty(queryConfig.getTargetProperty())) {
            queryConfig.setTargetProperty(DEFAULT_TARGET_PLACEHOLDER);
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

        int order = 1;
        List<FunctionParamPO> params = new ArrayList<>();
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
        if (!params.isEmpty()) {
            functionParamService.saveBatch(params);
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
     * 基础查询算子测试执行：变量替换 → 调用 genericQuery。
     * 聚合模式返回 BasicQueryResultVO，query 模式返回分页结果。
     */
    @SneakyThrows
    @Override
    public Object testBasicQuery(BasicQueryTestParam param) {
        // 1. 获取函数并解析 queryConfig
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        PreconditionUtils.checkArgument(function != null, "函数不存在：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(function.getType() == FunctionTypeEnum.BASIC_QUERY,
                "该函数不是基础查询算子", HttpStatus.BAD_REQUEST);
        var config = objectMapper.readValue(function.getCode(), BasicQueryConfig.class);

        var bindings = param.getVariableBindings();

        // 2. 构建 EntityPropertyGenericQueryParam
        var queryParam = new EntityPropertyGenericQueryParam();
        queryParam.setOntologyIdentifier(param.getOntologyIdentifier());

        // SELECT：通过变量绑定解析实际属性名
        String targetProp = null;
        List<OntologySelectPropertyParam> selectProps = new ArrayList<>();
        
        if (StringUtils.isNotEmpty(config.getTargetProperty())) {
            targetProp = bindings.get(config.getTargetProperty());
            PreconditionUtils.checkArgument(StringUtils.isNotEmpty(targetProp),
                    "变量 " + config.getTargetProperty() + " 未绑定", HttpStatus.BAD_REQUEST);
            var selectProp = new OntologySelectPropertyParam();
            selectProp.setPropertyApiName(targetProp);
            selectProp.setAggFunc(config.getAggFunc());
            if (config.getAggFunc() != null) {
                selectProp.setAlias(config.getAggFunc().name().toLowerCase() + "_" + targetProp);
            }
            selectProps.add(selectProp);
        }
        
        // query 模式下如果没有指定 targetProperty，查询本体的所有属性
        if (config.getAggFunc() == null && selectProps.isEmpty()) {
            var allProps = propertyMapper.selectList(
                    new LambdaQueryWrapper<OntologyProperty>()
                            .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier()));
            for (var prop : allProps) {
                var selectProp = new OntologySelectPropertyParam();
                selectProp.setPropertyApiName(prop.getApiName());
                selectProps.add(selectProp);
            }
        }
        
        if (!selectProps.isEmpty()) {
            queryParam.setSelectProperties(selectProps);
        }

        // WHERE：深拷贝 filters 并替换变量名
        if (config.getFilters() != null) {
            var resolvedFilters = replaceVariablesInFilterGroup(config.getFilters(), bindings);
            queryParam.setFilters(resolvedFilters);
        }

        // 3. 聚合模式 vs query 模式
        if (config.getAggFunc() != null) {
            // 聚合模式：返回 BasicQueryResultVO
            queryParam.setPageNum(1);
            queryParam.setPageSize(1);
            var pageResult = entityService.genericQuery(queryParam);
            Object aggValue = null;
            if (pageResult.getRecords() != null && !pageResult.getRecords().isEmpty()) {
                var firstRow = pageResult.getRecords().get(0);
                if (firstRow != null && !firstRow.isEmpty()) {
                    aggValue = firstRow.get(0).getValue();
                }
            }
            return BasicQueryResultVO.builder()
                    .aggFunc(config.getAggFunc().name())
                    .targetProperty(targetProp)
                    .alias(config.getAggFunc().name().toLowerCase() + "_" + targetProp)
                    .value(aggValue)
                    .build();
        } else {
            // query 模式：返回分页结果
            queryParam.setPageNum(param.getPageNum());
            queryParam.setPageSize(param.getPageSize());
            return entityService.genericQuery(queryParam);
        }
    }

    /**
     * 深拷贝过滤树并将变量名替换为实际属性 apiName。
     */
    private FilterGroupParam replaceVariablesInFilterGroup(FilterGroupParam group, Map<String, String> bindings) {
        if (group == null) return null;
        var newGroup = new FilterGroupParam();
        newGroup.setLogic(group.getLogic());
        if (CollectionUtils.isEmpty(group.getChildren())) return newGroup;

        List<FilterNodeParam> newChildren = new ArrayList<>();
        for (var node : group.getChildren()) {
            var newNode = new FilterNodeParam();
            newNode.setType(node.getType());
            if (node.getType() == FilterNodeTypeEnum.FILTER && node.getFilter() != null) {
                var newFilter = new PropertyFilterParam();
                String varName = node.getFilter().getPropertyApiName();
                String actualProp = bindings.get(varName);
                PreconditionUtils.checkArgument(StringUtils.isNotEmpty(actualProp),
                        "过滤变量 " + varName + " 未绑定", HttpStatus.BAD_REQUEST);
                newFilter.setPropertyApiName(actualProp);
                newFilter.setOp(node.getFilter().getOp());
                newFilter.setValue(node.getFilter().getValue());
                newFilter.setValues(node.getFilter().getValues());
                newFilter.setDataType(node.getFilter().getDataType());
                newNode.setFilter(newFilter);
            } else if (node.getType() == FilterNodeTypeEnum.GROUP && node.getGroup() != null) {
                newNode.setGroup(replaceVariablesInFilterGroup(node.getGroup(), bindings));
            }
            newChildren.add(newNode);
        }
        newGroup.setChildren(newChildren);
        return newGroup;
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
                // 复用 executeFunction 逻辑
                var executeParam = FunctionExecuteParam.builder()
                        .functionApi(param.getFunctionApi())
                        .parameters(param.getParameters())
                        .build();
                return executeFunction(executeParam);

            default:
                throw new BusinessException("不支持的函数类型：" + function.getType());
        }
    }


}
