package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.constant.FunctionConstant;
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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionServiceImpl extends ServiceImpl<FunctionMapper, Function> implements FunctionService {

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
    public Map<String, String> mapDescriptionByApi(List<String> functionApis) {
        if (functionApis == null || functionApis.isEmpty()) {
            return new HashMap<>();
        }
        // 版本管理后 api 不再唯一，同一 api 多个版本时描述取任意一条（toMap 保留先遇到的）；
        // description 列可为 null，过滤空白描述，避免 Collectors.toMap 在 value 为 null 时抛 NPE
        return list(new LambdaQueryWrapper<Function>()
                        .select(Function::getApi, Function::getDescription)
                        .in(Function::getApi, functionApis))
                .stream()
                .filter(function -> StringUtils.isNotBlank(function.getDescription()))
                .collect(Collectors.toMap(Function::getApi, Function::getDescription, (first, second) -> first));
    }


    @Override
    public FunctionDetailVO getFunctionDetailByApi(String api, String version) {
        var function = resolveVersion(api, version);
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
                .version(function.getVersion())
                .publishStatus(function.getPublish())
                .displayName(function.getDisplayName())
                .description(function.getDescription())
                .type(function.getType())
                .model(function.getModel())
                .ontologySpaceId(function.getOntologySpaceId())
                .build();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteByApi(String api, String version) {
        var function = resolveVersion(api, version);
        PreconditionUtils.checkArgument(function != null, "无效的函数api:" + api, HttpStatus.BAD_REQUEST);
        // 已发布状态不允许删除，需先下线
        requireUnpublished(function, "已发布的函数不能删除，请先下线");
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
        // apiName 与函数名称必填（@NotBlank 已在 Controller 层校验，此处纵深防御，防绕过 Controller 直接调用服务）
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(param.getFunctionApi()),
                "functionApi is empty", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(param.getDisplayName()),
                "displayName is empty", HttpStatus.BAD_REQUEST);
        // 自动填充 model：未传时根据 type 推断
        FunctionModelEnum model = param.getModel();
        if (model == null) {
            model = (param.getType() == FunctionTypeEnum.BASIC_QUERY) 
                    ? FunctionModelEnum.BASIC 
                    : FunctionModelEnum.OTHER;
        }
        String displayName = param.getDisplayName();
        // 版本管理：版本号必须大于同 api 下已有最大版本号（首次创建无此限制）
        String version = requireValidVersion(param.getVersion());
        List<Function> existVersions = listVersions(param.getFunctionApi());
        String newApi = param.getFunctionApi() + " " + version;
        PreconditionUtils.checkArgument(existVersions.stream().noneMatch(f -> version.equals(f.getVersion())),
                "函数版本已存在:" + newApi, HttpStatus.BAD_REQUEST);
        String maxVersion = existVersions.stream().map(Function::getVersion).max(FunctionServiceImpl::compareVersion).orElse(null);
        PreconditionUtils.checkArgument(maxVersion == null || compareVersion(version, maxVersion) > 0,
                "新版本号必须大于当前最大版本号 " + maxVersion, HttpStatus.BAD_REQUEST);
        //函数插入，新增版本默认为未发布状态
        var func = Function.builder()
                .api(param.getFunctionApi())
                .version(version)
                .publish(FunctionConstant.PUBLISH_UNPUBLISHED)
                .description(param.getDescription())
                .displayName(displayName)
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
        var function = resolveVersion(param.getFunctionApi(), param.getVersion());
        PreconditionUtils.checkArgument(function != null,
                "函数版本不存在：" + param.getFunctionApi() + " " + param.getVersion(), HttpStatus.BAD_REQUEST);
        // 已发布版本一律禁止修改，需先下线（无“另存新版本”口子，需要新版本请走创建接口）
        PreconditionUtils.checkArgument(function.getPublish() == FunctionConstant.PUBLISH_UNPUBLISHED,
                "已发布的函数不能修改，请先下线：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        // 原地修改前校验行为关联（与删除/下线一致的 api 级粒度），被行为引用则禁止直接改
        var ontologyActions = ontologyActionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getFunctionApi, function.getApi()));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(ontologyActions),
                "该函数已被行为关联，不能修改：" + function.getApi());
        // 未发布：原地更新（版本号不可改，param.version 仅作定位）
        applyEditableFields(function, param);
        updateById(function);
        // 参数重建：先删后建
        functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
        if (function.getType() == FunctionTypeEnum.CUSTOMIZE) {
            insertBatchFuncParams(function.getId(), param.getCode());
        } else if (function.getType() == FunctionTypeEnum.BASIC_QUERY) {
            // 内部校验 queryConfig 非空并重写 code
            createBasicQueryParams(function.getId(), param.getQueryConfig());
        }
        //失效编译缓存，释放旧 Class / ClassLoader
        groovyService.invalidateCompiledClass(function.getApi());
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void publishFunction(String api, String version) {
        var function = requireVersionExists(api, version);
        PreconditionUtils.checkArgument(function.getPublish() == FunctionConstant.PUBLISH_UNPUBLISHED,
                "该版本已是发布状态：" + api + " " + function.getVersion(), HttpStatus.BAD_REQUEST);
        function.setPublish(FunctionConstant.PUBLISH_PUBLISHED);
        updateById(function);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void unpublishFunction(String api, String version) {
        var function = requireVersionExists(api, version);
        PreconditionUtils.checkArgument(function.getPublish() == FunctionConstant.PUBLISH_PUBLISHED,
                "该版本不是发布状态：" + api + " " + function.getVersion(), HttpStatus.BAD_REQUEST);
        //判断函数是否关联了行为，被本体行为使用到则不能下线
        var ontologyActions = ontologyActionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getFunctionApi, api));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(ontologyActions), "该函数已被行为关联，不能下线：" + api);
        function.setPublish(FunctionConstant.PUBLISH_UNPUBLISHED);
        updateById(function);
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
                                              Integer publishStatus,
                                              String startDate, String endDate,
                                              Integer pageNum, Integer pageSize) {
        var pageInfo = new Page<Function>(pageNum, pageSize);
        // 版本分组分页：按 api 分组，每组仅展示版本号最大的一条（XML 内按 x.y.z 语义化比较）
        var functionPage = getBaseMapper().pageLatestVersion(pageInfo, ontologySpaceId, displayName, type,
                publishStatus, startDate, endDate);
        var records = functionPage.getRecords().stream().<FunctionInfoVO>map(func ->
                FunctionInfoVO.builder()
                        .functionApi(func.getApi())
                        .version(func.getVersion())
                        .publishStatus(func.getPublish())
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
        //查询函数（指定版本，缺省取最新版本）
        var function = resolveVersion(param.getFunctionApi(), param.getVersion());
        PreconditionUtils.checkArgument(function != null,
                "函数版本不存在：" + param.getFunctionApi() + " " + param.getVersion(), HttpStatus.BAD_REQUEST);
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
        // 1. 获取函数版本并解析 queryConfig
        var function = resolveVersion(param.getFunctionApi(), param.getVersion());
        PreconditionUtils.checkArgument(function != null, "函数版本不存在：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
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
        var function = resolveVersion(param.getFunctionApi(), param.getVersion());
        PreconditionUtils.checkArgument(function != null, "函数版本不存在：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);

        switch (function.getType()) {
            case BASIC_QUERY:
                // 转换为 BasicQueryTestParam 并调用 testBasicQuery
                var basicParam = BasicQueryTestParam.builder()
                        .functionApi(param.getFunctionApi())
                        .version(function.getVersion())
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
                        .version(function.getVersion())
                        .parameters(param.getParameters())
                        .build();
                return executeFunction(executeParam);

            default:
                throw new BusinessException("不支持的函数类型：" + function.getType());
        }
    }

    // ==================== 版本管理私有方法 ====================

    /**
     * 解析目标版本：version 为空时按 x.y.z 语义化比较取该 api 的最大版本
     */
    private Function resolveVersion(String api, String version) {
        if (StringUtils.isNotEmpty(version)) {
            return getOne(new LambdaQueryWrapper<Function>()
                    .eq(Function::getApi, api)
                    .eq(Function::getVersion, version));
        }
        return listVersions(api).stream()
                .max(Comparator.comparing(Function::getVersion, FunctionServiceImpl::compareVersion))
                .orElse(null);
    }

    /**
     * 查询指定 api 的全部版本
     */
    private List<Function> listVersions(String api) {
        return list(new LambdaQueryWrapper<Function>().eq(Function::getApi, api));
    }

    /**
     * 解析目标版本，不存在则抛业务异常（发布/下线接口使用，版本号必传）
     */
    private Function requireVersionExists(String api, String version) {
        var function = resolveVersion(api, version);
        PreconditionUtils.checkArgument(function != null,
                "函数版本不存在：" + api + (StringUtils.isEmpty(version) ? "" : " " + version), HttpStatus.BAD_REQUEST);
        return function;
    }

    /**
     * 校验并发布状态：非未发布则抛业务异常
     */
    private void requireUnpublished(Function function, String message) {
        PreconditionUtils.checkArgument(function.getPublish() == FunctionConstant.PUBLISH_UNPUBLISHED, message);
    }

    /**
     * 校验版本号格式 x.y.z，返回规范化后的版本号
     */
    private String requireValidVersion(String version) {
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(version) && version.matches(FunctionConstant.VERSION_PATTERN),
                "版本号格式必须为 x.y.z（如 1.0.0）：" + version, HttpStatus.BAD_REQUEST);
        return version;
    }

    /**
     * 语义化版本号逐段数值比较，段数不足按 0 补齐（如 1.10.0 > 1.9.0）
     */
    static int compareVersion(String left, String right) {
        String[] l = left.split("\\.");
        String[] r = right.split("\\.");
        int len = Math.max(l.length, r.length);
        for (int i = 0; i < len; i++) {
            long lv = i < l.length ? Long.parseLong(l[i]) : 0L;
            long rv = i < r.length ? Long.parseLong(r[i]) : 0L;
            int cmp = Long.compare(lv, rv);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /**
     * 将更新参数中的可编辑字段应用到函数实体（type 特有内容另处理）
     */
    private void applyEditableFields(Function function, FunctionUpdateParam param) {
        function.setDescription(param.getDescription());
        // 修改时 api 与函数名称不可变更：api 仅用于定位不会变，displayName 不赋值保留原值
        if (param.getModel() != null) {
            function.setModel(param.getModel());
        }
        if (StringUtils.isNotEmpty(param.getReferenceName())) {
            function.setReferenceName(param.getReferenceName());
        }
        if (function.getType() == FunctionTypeEnum.CUSTOMIZE) {
            function.setCode(param.getCode());
        } else if (function.getType() == FunctionTypeEnum.BASIC_QUERY) {
            // code 由后续 createBasicQueryParams 序列化 queryConfig 重新写入，此处不覆盖旧值
            PreconditionUtils.checkArgument(param.getQueryConfig() != null,
                    "基础查询算子更新必须提供 queryConfig", HttpStatus.BAD_REQUEST);
        }
    }

}
