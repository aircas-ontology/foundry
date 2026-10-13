package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.constant.FunctionConstant;
import com.aircas.ptr.foundry.ontology.engine.ScriptEngineRouter;
import com.aircas.ptr.foundry.ontology.engine.ScriptExecutionContext;
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
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
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
import com.aircas.ptr.foundry.ontology.utils.SchemaHandleUtil;
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
import java.util.LinkedHashMap;
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

    @Resource
    private ScriptEngineRouter scriptEngineRouter;

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
                .version(function.getVersion())
                .publishStatus(function.getPublish())
                .displayName(function.getDisplayName())
                .description(function.getDescription())
                .type(function.getType())
                .language(function.getLanguage())
                .model(function.getModel())
                .ontologySpaceId(function.getOntologySpaceId())
                .build();
    }

    @Override
    public List<String> listVersionsByApi(String api) {
        if (StringUtils.isEmpty(api)) {
            return new ArrayList<>();
        }
        // 同一 api 下全部版本号，按语义化版本倒序（最新在前）
        return listVersions(api).stream()
                .map(Function::getVersion)
                .sorted((v1, v2) -> compareVersion(v2, v1))
                .collect(Collectors.toList());
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
//失效对应语言 runner 的编译/产物缓存
        scriptEngineRouter.route(function.getLanguage()).invalidate(function.getLanguage(), api);
        //失效编译缓存，释放旧 Class / ClassLoader
        groovyService.invalidateCompiledClass(api);
        //卸载编排执行链，避免删除后仍命中旧的 LiteFlow 链定义
        compositeChainRegistry.evict(api);
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
        //language 仅 CUSTOMIZE 脚本算子有意义；基础查询/外部/容器算子存 NULL（EXTERNAL 执行链路待定，开发时再评估）
        FunctionLanguageEnum language = null;
        if (param.getType() == FunctionTypeEnum.CUSTOMIZE) {
            language = param.getLanguage() == null ? FunctionLanguageEnum.GROOVY : param.getLanguage();
            //脚本函数落库前先做语法/编译校验（走对应 runner 的 /validate），失败即拒、不落库
            validateScript(language, param.getFunctionApi(), version, param.getCode());
        }
        var func = Function.builder()
                .api(param.getFunctionApi())
                .version(version)
                .publish(FunctionConstant.PUBLISH_UNPUBLISHED)
                .description(param.getDescription())
                .displayName(displayName)
                .code(param.getCode())
                .type(param.getType())
                .language(language)
                .model(model)
                .status(Status.ENABLE.getValue())
                .referenceName(param.getReferenceName())
                .ontologySpaceId(param.getOntologySpaceId())
                .build();
        save(func);
        //自定义函数需要解析函数参数
        if (param.getType().equals(FunctionTypeEnum.CUSTOMIZE)) {
            //参数入库：前端 IDE 声明优先，Groovy 未声明时回退后端反射解析
            buildCustomizeParams(param.getFunctionApi(), func.getId(), language, param.getCode(), param.getParameters());
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
        //脚本函数更新前先校验新代码（语言锁定不可改，取 function.language）
        if (function.getType() == FunctionTypeEnum.CUSTOMIZE) {
            validateScript(function.getLanguage(), function.getApi(), function.getVersion(), param.getCode());
        }
        applyEditableFields(function, param);
        updateById(function);
        // 参数重建：CUSTOMIZE 解析成功才删旧建新（解析失败保留原定义）；BASIC_QUERY/COMPOSITE/EXTERNAL 先删后建
        if (function.getType() == FunctionTypeEnum.CUSTOMIZE) {
            rebuildCustomizeParams(function.getApi(), function.getId(), function.getLanguage(), param.getCode(), param.getParameters());
        } else if (function.getType() == FunctionTypeEnum.BASIC_QUERY) {
            functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
            // 内部校验 queryConfig 非空并重写 code
            createBasicQueryParams(function.getId(), param.getQueryConfig());
        } else if (function.getType() == FunctionTypeEnum.COMPOSITE) {
            functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
            // 未重新提交画布配置时沿用库中已存定义，避免画布被清空
            CompositeConfig compositeConfig = param.getCompositeConfig() != null
                    ? param.getCompositeConfig()
                    : parseCompositeConfig(function.getCode());
            createCompositeParams(function.getId(), function.getApi(), compositeConfig);
        } else if (function.getType() == FunctionTypeEnum.EXTERNAL) {
            functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
            // 未重新提交接口定义时沿用库中已存定义
            ExternalApiConfig apiConfig = param.getApiConfig() != null
                    ? param.getApiConfig()
                    : parseExternalConfig(function.getCode(), function.getApi());
            createExternalParams(function.getId(), apiConfig);
        }
        //失效对应语言 runner 的编译/产物缓存
        scriptEngineRouter.route(function.getLanguage()).invalidate(function.getLanguage(), function.getApi());
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

    /**
     * 脚本语法/编译校验：走对应语言 runner 的 /validate，失败抛业务异常。
     * 仅 CUSTOMIZE 脚本函数需要；无代码时跳过（BASIC_QUERY/EXTERNAL 不走脚本校验）。
     */
    private void validateScript(FunctionLanguageEnum language, String functionApi, String version, String code) {
        if (StringUtils.isEmpty(code)) {
            return;
        }
        FunctionLanguageEnum lang = language == null ? FunctionLanguageEnum.GROOVY : language;
        ScriptExecutionContext ctx = ScriptExecutionContext.builder()
                .language(lang)
                .functionApi(functionApi)
                .version(version)
                .code(code)
                .build();
        scriptEngineRouter.route(lang).validate(ctx);
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
                        .language(func.getLanguage())
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
//函数编排：走 LiteFlow 执行链，不复用 groovy 脚本执行逻辑
        if (function.getType() == FunctionTypeEnum.COMPOSITE) {
            return executeCompositeFunction(function, funParamMap);
        }
        //基础查询算子/外部API算子：与编排节点共用同一套执行语义
        if (function.getType() == FunctionTypeEnum.BASIC_QUERY
                || function.getType() == FunctionTypeEnum.EXTERNAL) {
            return executeOperatorFunction(function, funParamMap, executeInputParams);
        }
        //脚本函数：按参数定义组装单对象入参（handle(input)），按语言路由到对应引擎执行
        Map<String, Object> inputMap = buildInputMap(executeInputParams, funParamMap);
        String inputJson = objectMapper.writeValueAsString(inputMap);
        FunctionLanguageEnum language = function.getLanguage() == null ? FunctionLanguageEnum.GROOVY : function.getLanguage();
        ScriptExecutionContext ctx = ScriptExecutionContext.builder()
                .language(language)
                .functionApi(param.getFunctionApi())
                .version(function.getVersion())
                .code(function.getCode())
                .inputJson(inputJson)
                .inputMap(inputMap)
                .inputParams(executeInputParams)
                .build();
        var resultJsonStr = scriptEngineRouter.route(language).execute(ctx);
        // 脚本返回字段由用户代码决定（可带 status 等自定义键），FunctionResultVO 已用 @JsonAnySetter 承接未知键
        var resultNode = objectMapper.readTree(resultJsonStr);
        String asyncTaskId = resultNode != null && resultNode.hasNonNull("taskId") ? resultNode.get("taskId").asText() : null;
        if (StringUtils.isNotEmpty(asyncTaskId)) {
            executeResultMapper.insert(FunctionExecuteResult.builder()
                    .taskId(asyncTaskId)
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
        // 1. 获取函数版本并解析 queryConfig
        var function = resolveVersion(param.getFunctionApi(), param.getVersion());
        PreconditionUtils.checkArgument(function != null, "函数版本不存在：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(function.getType() == FunctionTypeEnum.BASIC_QUERY,
                "该函数不是基础查询算子", HttpStatus.BAD_REQUEST);
        return basicQueryOperator.run(basicQueryOperator.parseConfig(function.getCode(), function.getApi()),
                param.getOntologyIdentifier(), param.getVariableBindings(), param.getPageNum(), param.getPageSize());
    }

    /***
     * 自定义函数参数入库（创建）：解析不出参数时只记 warn、不建参数（无参数时执行 input 为空对象）。
     */
    private void buildCustomizeParams(String functionApi, Long functionId, FunctionLanguageEnum language, String code, List<FunctionParamDTO> declared) {
        List<FunctionParamDTO> resolved = resolveCustomizeParams(language, code, declared);
        if (CollectionUtils.isEmpty(resolved)) {
            if (resolved == null) {
                log.warn("脚本参数未能自动解析，跳过参数入库：functionApi={}, functionId={}, language={}", functionApi, functionId, language);
            }
            return;
        }
        saveParams(functionId, resolved);
    }

    /**
     * 自定义函数参数重建（更新）：解析失败（返回 null）时保留原有参数定义，避免误删前端已声明的参数。
     */
    private void rebuildCustomizeParams(String functionApi, Long functionId, FunctionLanguageEnum language, String code, List<FunctionParamDTO> declared) {
        List<FunctionParamDTO> resolved = resolveCustomizeParams(language, code, declared);
        if (resolved == null) {
            log.warn("脚本参数未能自动解析，保留原有参数定义：functionApi={}, functionId={}, language={}", functionApi, functionId, language);
            return;
        }
        functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, functionId));
        saveParams(functionId, resolved);
    }

    /**
     * 解析自定义函数的参数定义：
     *   1) 前端 IDE 声明的参数优先（四语言统一契约）；
     *   2) Groovy 未声明时回退后端反射解析，且仅老契约脚本（handle 返回 FunctionResultVO）能解析成功；
     *   3) 新契约脚本 handle(input) 为单对象入参，反射拿不到字段名，返回 null 表示“无法确定”，
     *      由调用方决定创建时不建参数 / 更新时保留原参数。
     */
    private List<FunctionParamDTO> resolveCustomizeParams(FunctionLanguageEnum language, String code, List<FunctionParamDTO> declared) {
        if (CollectionUtils.isNotEmpty(declared)) {
            return declared;
        }
        if (language != null && language != FunctionLanguageEnum.GROOVY) {
            return null;
        }
        try {
            return groovyService.parseGroovyCode(code);
        } catch (Exception e) {
            log.warn("Groovy 反射解析参数失败（新契约脚本属正常现象）：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 将参数定义（前端声明或 Groovy 解析）批量写入 function_param。
     */
    private void saveParams(Long functionId, List<FunctionParamDTO> functionParam) {
        if (CollectionUtils.isEmpty(functionParam)) {
            return;
        }
        List<FunctionParamPO> params = functionParam.stream().map(p ->
                FunctionParamPO.builder()
                        .functionId(functionId)
                        .paramName(p.getParamName())
                        .paramType(p.getParamType())
                        .category(p.getCategory())
                        .paramSchema(p.getParamSchema())
                        .paramOrder(p.getParamOrder())
                        .typeReferenceName(p.getReferenceType())
                        .description(p.getDescription())
                        .createTime(new Date())
                        .updateTime(new Date())
                        .build()
        ).collect(Collectors.toList());
        functionParamService.saveBatch(params);
    }

    /**
     * 按入参定义把执行参数值组装成 `handle(input)` 的 input。
     * 基本类型走 SchemaHandleUtil 转换；OBJECT 直接透传原始值（统一单对象入参契约后支持复杂类型）。
     * typeReferenceName 为空时不做类型转换，直接透传（兼容非 JVM 语言前端声明参数未提供 Java 类名的场景）。
     * 未赋值（value 为 null）的参数不写入 input：各语言对“键存在但值为 null”的处理不一致
     * （Python input.get('name','world') 会拿到 None 而非默认值，只有 Groovy 的 input.name ?: 'world' 能兜底），
     * 省略缺值键可让脚本自己的默认值在各语言统一生效。
     */
    static Map<String, Object> buildInputMap(List<FunctionParamPO> inputParams, Map<String, Object> funParamMap) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (CollectionUtils.isNotEmpty(inputParams)) {
            for (FunctionParamPO p : inputParams) {
                Object value = funParamMap.get(p.getParamName());
                if (value == null) {
                    continue;
                }
                if (p.getParamType() == FunctionParamTypeEnum.OBJECT
                        || StringUtils.isEmpty(p.getTypeReferenceName())) {
                    input.put(p.getParamName(), value);
                } else {
                    input.put(p.getParamName(), SchemaHandleUtil.convertValue(value, p.getTypeReferenceName()));
                }
            }
        }
        return input;
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
            case COMPOSITE:
                // 复用 executeFunction 逻辑：脚本走 runner 引擎，外部API走接口定义，编排走 LiteFlow 链
                // 脚本自定义返回键由 FunctionResultVO.extra 承接并平铺回传，不会因未知字段报错
                var executeParam = FunctionExecuteParam.builder()
                        .functionApi(param.getFunctionApi())
                        .version(function.getVersion())
                        .parameters(param.getParameters())
                        .build();
                var resultJson = executeFunction(executeParam);
                return objectMapper.readValue(resultJson, new TypeReference<FunctionResultVO>() {
                });

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
