package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.constant.FunctionConstant;
import com.aircas.ptr.foundry.ontology.engine.ScriptEngineRouter;
import com.aircas.ptr.foundry.ontology.engine.ScriptExecutionContext;
import com.aircas.ptr.foundry.ontology.composite.BasicQueryOperator;
import com.aircas.ptr.foundry.ontology.composite.CompositeChainRegistry;
import com.aircas.ptr.foundry.ontology.composite.CompositeFunctionInvoker;
import com.aircas.ptr.foundry.ontology.composite.ContainerOperatorInvoker;
import com.aircas.ptr.foundry.ontology.composite.ExternalApiOperator;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeValidationBO;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerOperatorConfig;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamRoleEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionRuntimeStatusEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ContainerPhaseEnum;
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
import com.aircas.ptr.foundry.ontology.model.param.FunctionListQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionParameter;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySelectPropertyParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyFilterParam;
import com.aircas.ptr.foundry.ontology.model.enums.FilterNodeTypeEnum;
import com.aircas.ptr.foundry.ontology.model.vo.BasicQueryResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyGenericQueryVO;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.FunctionExecuteResult;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionExecuteResultMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionContainerInstanceMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyPropertyMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.aircas.ptr.foundry.ontology.utils.DockerCommandExecutor;
import com.aircas.ptr.foundry.ontology.utils.OperatorFunctionFactory;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionServiceImpl extends ServiceImpl<FunctionMapper, Function> implements FunctionService {

    @Resource
    private OntologyActionMapper ontologyActionMapper;

    /** 容器算子运行实例 Mapper：详情回显按 container_key 取容器级配置，删除按 key 引用计数 */
    @Resource
    private FunctionContainerInstanceMapper containerInstanceMapper;

    /** docker 命令执行器：删除容器最后一个算子时停止并移除常驻容器 */
    @Resource
    private DockerCommandExecutor dockerCommandExecutor;

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

    /** 容器算子执行器：独立执行/测试链路按 containerKey 定位运行中的容器并发起 HTTP 调用（编排引用暂未开放） */
    @Resource
    private ContainerOperatorInvoker containerOperatorInvoker;

    private ObjectMapper objectMapper = new ObjectMapper()
            // 存进库的 fields 是 FunctionParamDTO 序列化结果（含 referenceType 等 VO 没有的字段），
            // 详情读回时反序列化为 FunctionParameterVO，需忽略未知字段，否则抛异常导致层级结构丢失
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 内存级运行状态跟踪：正在执行的 functionApi 集合，列表查询时据此填充 runningStatus */
    private final Set<String> runningFunctions = Collections.newSetFromMap(new ConcurrentHashMap<>());


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
        var params = functionParams.stream().map(p -> {
            FunctionParameterVO.FunctionParameterVOBuilder voBuilder = FunctionParameterVO.builder()
                    .paramId(p.getId())
                    .paramName(p.getParamName())
                    .category(p.getCategory())
                    .paramOrder(p.getParamOrder())
                    .paramSchema(p.getParamSchema())
                    .paramType(p.getParamType())
                    .description(p.getDescription());
            // 反序列化 fields JSON 为嵌套结构
            if (StringUtils.isNotEmpty(p.getFields())) {
                try {
                    List<FunctionParameterVO> fieldVOs = objectMapper.readValue(
                            p.getFields(), new TypeReference<List<FunctionParameterVO>>() {});
                    voBuilder.fields(fieldVOs);
                } catch (JsonProcessingException e) {
                    log.warn("反序列化参数 fields 失败：paramName={}, cause={}", p.getParamName(), e.getMessage());
                }
            }
            return voBuilder.build();
        }).collect(Collectors.toList());

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

        // EXTERNAL 类型：解析接口定义，前端据此回显 inputs/outputs
        ExternalApiConfig apiConfig = null;
        if (function.getType() == FunctionTypeEnum.EXTERNAL && StringUtils.isNotEmpty(function.getCode())) {
            try {
                apiConfig = objectMapper.readValue(function.getCode(), ExternalApiConfig.class);
            } catch (JsonProcessingException e) {
                log.warn("解析外部API算子配置失败: {}", api, e);
            }
        }

        // CONTAINER 类型：docker_config 只存接口级（interfaces），容器级字段在 function_container_instance，
        // 按 container_key 查实例后重组为完整配置回显，保持前端契约不变
        ContainerOperatorConfig dockerConfig = null;
        if (function.getType() == FunctionTypeEnum.CONTAINER) {
            dockerConfig = assembleContainerConfig(function);
        }

        return FunctionDetailVO.builder()
                .code(function.getCode())
                .referenceName(function.getReferenceName())
                .params(params)
                .queryConfig(queryConfig)
                .compositeConfig(compositeConfig)
                .apiConfig(apiConfig)
                .dockerConfig(dockerConfig)
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
        // 容器算子：若引用同一镜像/接口文档文件的全部算子都已删除，清理磁盘文件
        cleanupContainerFilesIfUnused(function);
    }

    /**
     * 删除容器算子后按 container_key 引用计数清理：
     * 若该 key 下已无任何存留 function（最后一个接口算子被删），则删除运行实例行、并清理镜像/接口文档磁盘文件。
     * <p>一容器=多条 function 共用同一 container_key，故须等全部关联算子删完才动容器与文件。
     * docker 容器本身的停止/移除随生命周期服务（t4/t8）接入，本处先保证实例行与磁盘文件不泄漏。</p>
     */
    private void cleanupContainerFilesIfUnused(Function deleted) {
        if (deleted.getType() != FunctionTypeEnum.CONTAINER
                || StringUtils.isEmpty(deleted.getContainerKey())) {
            return;
        }
        String containerKey = deleted.getContainerKey();
        // 该函数已从库中删除；统计同 key 下仍存留的接口算子数，仍有则不动容器与文件
        long remaining = count(new LambdaQueryWrapper<Function>()
                .eq(Function::getContainerKey, containerKey));
        if (remaining > 0) {
            return;
        }
        FunctionContainerInstance instance = findInstanceByKey(containerKey);
        if (instance == null) {
            return;
        }
        // 曾启动过（containerId 非空）则先停止并移除常驻容器，再释放镜像/文档文件（否则文件可能占用中）
        if (StringUtils.isNotEmpty(instance.getContainerId())) {
            stopAndRemoveContainerQuietly(instance.getContainerId());
        }
        deleteFileQuietly(pathOf(readJson(instance.getImage(), ContainerImageInfo.class), ContainerImageInfo::getPath), "镜像");
        deleteFileQuietly(pathOf(readJson(instance.getInterfaceDoc(), ContainerInterfaceDoc.class), ContainerInterfaceDoc::getPath), "接口文档");
        containerInstanceMapper.deleteById(instance.getId());
    }

    /** 停止并移除容器（docker stop + rm -f）：幂等且 best-effort，失败仅记日志，不影响 DB 删除与文件清理 */
    private void stopAndRemoveContainerQuietly(String containerId) {
        try {
            dockerCommandExecutor.stop(containerId);
            dockerCommandExecutor.remove(containerId);
            log.info("容器算子已全部删除，已停止并移除容器：{}", containerId);
        } catch (Exception e) {
            log.warn("停止/移除容器失败（容器 ID：{}），继续清理：{}", containerId, e.getMessage());
        }
    }

    /** 按 container_key 查运行实例；无则返回 null */
    private FunctionContainerInstance findInstanceByKey(String containerKey) {
        if (StringUtils.isEmpty(containerKey)) {
            return null;
        }
        return containerInstanceMapper.selectOne(new LambdaQueryWrapper<FunctionContainerInstance>()
                .eq(FunctionContainerInstance::getContainerKey, containerKey));
    }

    /** 详情回显：docker_config 仅含接口级，按 container_key 查实例补齐容器级字段，重组为完整 ContainerOperatorConfig */
    private ContainerOperatorConfig assembleContainerConfig(Function function) {
        ContainerOperatorConfig config = readJson(function.getDockerConfig(), ContainerOperatorConfig.class);
        if (config == null) {
            config = ContainerOperatorConfig.builder().build();
        }
        FunctionContainerInstance instance = findInstanceByKey(function.getContainerKey());
        if (instance != null) {
            config.setImage(readJson(instance.getImage(), ContainerImageInfo.class));
            config.setInterfaceDoc(readJson(instance.getInterfaceDoc(), ContainerInterfaceDoc.class));
            config.setRunCommand(instance.getRunCommand());
            config.setHostPort(instance.getHostPort());
            config.setContainerPort(instance.getContainerPort());
        }
        return config;
    }

    /** 取对象的路径字段；对象为 null 时返回 null */
    private <T> String pathOf(T obj, java.util.function.Function<T, String> getter) {
        return obj == null ? null : getter.apply(obj);
    }

    /** 删除磁盘文件，忽略不存在/IO 异常（不影响 DB 事务） */
    private void deleteFileQuietly(String path, String kind) {
        if (StringUtils.isEmpty(path)) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(path));
            log.info("容器算子已全部删除，清理{}文件：{}", kind, path);
        } catch (IOException e) {
            log.warn("清理容器算子{}文件失败：{}，cause={}", kind, path, e.getMessage());
        }
    }

    /** 通用 JSON 读取；空串或解析失败返回 null */
    private <T> T readJson(String json, Class<T> clazz) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.warn("解析容器算子 JSON 失败({})：{}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
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
        // 基于旧版本创建 vs 全新创建的 api 重复校验策略
        Boolean basedOnOldVersion = param.getBasedOnOldVersion();
        if (basedOnOldVersion == null || !basedOnOldVersion) {
            // 全新创建：api 不能与任何已存在的函数重复（即使版本不同）
            boolean apiExists = count(new LambdaQueryWrapper<Function>()
                    .eq(Function::getApi, param.getFunctionApi())) > 0;
            PreconditionUtils.checkArgument(!apiExists,
                    "函数 api 已存在:" + param.getFunctionApi() + "，全新创建请使用不同的 api 名称", HttpStatus.BAD_REQUEST);
        }
        // 如果 basedOnOldVersion = true，则允许相同 api，只校验 api + version 不重复（已在上面校验）
        //函数插入，新增版本默认为未发布状态
        //language 仅 CUSTOMIZE 脚本算子有意义；基础查询/外部/容器算子存 NULL（EXTERNAL 执行链路待定，开发时再评估）
        FunctionLanguageEnum language = null;
        if (param.getType() == FunctionTypeEnum.CUSTOMIZE) {
            language = param.getLanguage() == null ? FunctionLanguageEnum.GROOVY : param.getLanguage();
            //脚本函数落库前先做语法/编译校验（走对应 runner 的 /validate），失败即拒、不落库
            validateScript(language, param.getFunctionApi(), version, param.getCode());
        }
        var func = OperatorFunctionFactory.operatorDefaults(
                        param.getFunctionApi(), version, displayName, param.getType(),
                        param.getOntologySpaceId(), model)
                .description(param.getDescription())
                .code(param.getCode())
                .language(language)
                .referenceName(param.getReferenceName())
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
        //外部API算子：序列化 apiConfig 到 code，并按 inputs/outputs 生成出入参，使其可接入编排工作流
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
        // 容器算子不支持编辑：镜像/接口配置在注册时一次性确定，如需调整请删除后重新创建
        PreconditionUtils.checkArgument(function.getType() != FunctionTypeEnum.CONTAINER,
                "容器算子不支持编辑，请删除后重新创建：" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
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
    public Page<FunctionInfoVO> getFunctions(FunctionListQueryParam param) {
        var pageInfo = new Page<Function>(param.getPageNum(), param.getPageSize());
        // 版本分组分页：按 api 分组，每组仅展示版本号最大的一条（XML 内按 x.y.z 语义化比较）
        var functionPage = getBaseMapper().pageLatestVersion(pageInfo, param.getOntologySpaceId(), param.getDisplayName(),
                param.getType(), param.getPublishStatus(), param.getStartDate(), param.getEndDate());
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
                        .createTime(func.getCreateTime())
                        .updateTime(func.getUpdateTime())
                        .runningStatus(runningFunctions.contains(func.getApi())
                                ? FunctionRuntimeStatusEnum.RUNNING
                                : FunctionRuntimeStatusEnum.NOT_RUNNING)
                        .containerKey(func.getContainerKey())
                        .build()
        ).collect(Collectors.toList());

        // 容器算子：批量查运行实例 phase，回填 containerPhase
        List<String> containerKeys = records.stream()
                .filter(r -> r.getType() == FunctionTypeEnum.CONTAINER && r.getContainerKey() != null)
                .map(FunctionInfoVO::getContainerKey)
                .collect(Collectors.toList());
        if (!containerKeys.isEmpty()) {
            Map<String, ContainerPhaseEnum> phaseMap = containerInstanceMapper.selectList(
                    new LambdaQueryWrapper<FunctionContainerInstance>()
                            .in(FunctionContainerInstance::getContainerKey, containerKeys)
                            .select(FunctionContainerInstance::getContainerKey, FunctionContainerInstance::getPhase)
            ).stream().collect(Collectors.toMap(
                    FunctionContainerInstance::getContainerKey,
                    FunctionContainerInstance::getPhase,
                    (a, b) -> a));
            records.stream()
                    .filter(r -> r.getType() == FunctionTypeEnum.CONTAINER && r.getContainerKey() != null)
                    .forEach(r -> r.setContainerPhase(phaseMap.get(r.getContainerKey())));
        }

        return new Page<FunctionInfoVO>()
                .setRecords(records)
                .setTotal(functionPage.getTotal())
                .setCurrent(functionPage.getCurrent())
                .setSize(functionPage.getSize());
    }

    @Override
    @SneakyThrows
    public String executeFunction(FunctionExecuteParam param) {
        runningFunctions.add(param.getFunctionApi());
        try {
            return doExecuteFunction(param);
        } finally {
            runningFunctions.remove(param.getFunctionApi());
        }
    }

    @SneakyThrows
    private String doExecuteFunction(FunctionExecuteParam param) {
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
                new HashMap<>() : param.getParameters().stream().collect(HashMap::new, (m, p) -> {
                    Object v = resolveParamValue(p);
                    if (v != null) {
                        m.put(p.getParamName(), v);
                    }
                }, HashMap::putAll);
//函数编排：走 LiteFlow 执行链，不复用 groovy 脚本执行逻辑
        if (function.getType() == FunctionTypeEnum.COMPOSITE) {
            return executeCompositeFunction(function, funParamMap);
        }
        //基础查询算子/外部API算子：与编排节点共用同一套执行语义
        if (function.getType() == FunctionTypeEnum.BASIC_QUERY
                || function.getType() == FunctionTypeEnum.EXTERNAL) {
            return executeOperatorFunction(function, funParamMap, executeInputParams);
        }
        //容器算子：按 containerKey 定位运行中的常驻容器，按其接口定义发起 HTTP 调用
        if (function.getType() == FunctionTypeEnum.CONTAINER) {
            return executeContainerFunction(function, funParamMap);
        }
        //脚本函数：按参数定义组装单对象入参（handle(input)），按语言路由到对应引擎执行
        Map<String, Object> inputMap = buildInputMap(executeInputParams, funParamMap);
        // 单对象入参解包：当只有一个 OBJECT 入参时，直接将该对象的值作为 runner 的 input，
        // 避免脚本里写 input.input.xxx 的双层嵌套（用户给参数起名 input/user/whatever 都能直接用 input.xxx）
        String inputJson;
        if (executeInputParams.size() == 1
                && executeInputParams.get(0).getParamType() == FunctionParamTypeEnum.OBJECT
                && inputMap.containsKey(executeInputParams.get(0).getParamName())) {
            Object value = inputMap.get(executeInputParams.get(0).getParamName());
            inputJson = objectMapper.writeValueAsString(value);
        } else {
            inputJson = objectMapper.writeValueAsString(inputMap);
        }
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
     * 执行单算子（基础查询/外部API）：复用编排节点调用器，直接返回执行结果的数据体 JSON，
     * 不再额外包一层 {@code FunctionResultVO}（历史包裹层中的 startTime / description 无业务价值）。
     */
    private String executeOperatorFunction(Function function, Map<String, Object> funParamMap,
                                           List<FunctionParamPO> inputParams) throws JsonProcessingException {
        Object raw = compositeFunctionInvoker.invoke(function, null, funParamMap, inputParams);
        Map<String, Object> data = raw instanceof Map<?, ?> map ? toStringKeyMap(map) : Map.of("result", raw);
        return objectMapper.writeValueAsString(data);
    }

    /**
     * 执行容器算子：委托 {@link ContainerOperatorInvoker} 按 containerKey 定位运行容器并发起 HTTP 调用，
     * 直接返回容器接口响应的数据体 JSON，不再额外包一层 {@code FunctionResultVO}。
     */
    private String executeContainerFunction(Function function, Map<String, Object> funParamMap)
            throws JsonProcessingException {
        Object raw = containerOperatorInvoker.invoke(function, funParamMap);
        Map<String, Object> data = raw instanceof Map<?, ?> map ? toStringKeyMap(map) : Map.of("result", raw);
        return objectMapper.writeValueAsString(data);
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

        // 仅持久化用户在 queryConfig 里声明的变量（聚合目标 + 过滤条件）作为入参，
        // 不追加编排机制参数（本体标识、结果出参等），避免详情页把机制参数当成用户参数展示。
        List<FunctionParamPO> params = new ArrayList<>();
        int order = 1;
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

    /** 入参端口：直接取 apiConfig.inputs */
    private List<ExternalApiConfig.PortDefinition> externalInputPorts(ExternalApiConfig apiConfig) {
        return apiConfig.inputsOrEmpty();
    }

    /** 出参端口：直接取 apiConfig.outputs；为空时兜底 result/status */
    private List<ExternalApiConfig.PortDefinition> externalOutputPorts(ExternalApiConfig apiConfig) {
        List<ExternalApiConfig.PortDefinition> outputs = apiConfig.outputsOrEmpty();
        if (!outputs.isEmpty()) {
            return outputs;
        }
        List<ExternalApiConfig.PortDefinition> defaults = new ArrayList<>();
        defaults.add(ExternalApiConfig.PortDefinition.builder()
                .name(ExternalApiOperator.PORT_RESULT).type("object").description("响应报文").build());
        defaults.add(ExternalApiConfig.PortDefinition.builder()
                .name(ExternalApiOperator.PORT_STATUS).type("number").description("HTTP 状态码").build());
        return defaults;
    }

    private FunctionParamPO externalParam(Long functionId, ExternalApiConfig.PortDefinition port,
                                          FunctionParamCategoryEnum category, int paramOrder) {
        return OperatorFunctionFactory.functionParamDefaults(
                        functionId, port.getName(), port.getType(), category, paramOrder, port.getDescription())
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
     * 前端未传 paramOrder 时，按同 category 内列表顺序自动编号。
     */
    private void saveParams(Long functionId, List<FunctionParamDTO> functionParam) {
        if (CollectionUtils.isEmpty(functionParam)) {
            return;
        }
        // 按 category 独立计数器，前端未传 paramOrder 时自动填充
        Map<FunctionParamCategoryEnum, Integer> orderCounters = new HashMap<>();
        List<FunctionParamPO> params = functionParam.stream().map(p -> {
            int order = p.getParamOrder() != null ? p.getParamOrder()
                    : orderCounters.merge(p.getCategory(), 1, Integer::sum);
            // 序列化 fields 为 JSON 存储
            String fieldsJson = null;
            if (p.getFields() != null && !p.getFields().isEmpty()) {
                try {
                    fieldsJson = objectMapper.writeValueAsString(p.getFields());
                } catch (JsonProcessingException e) {
                    log.warn("序列化参数 fields 失败：paramName={}, cause={}", p.getParamName(), e.getMessage());
                }
            }
            return FunctionParamPO.builder()
                    .functionId(functionId)
                    .paramName(p.getParamName())
                    .paramType(p.getParamType())
                    .category(p.getCategory())
                    .paramSchema(p.getParamSchema())
                    .paramOrder(order)
                    .typeReferenceName(p.getReferenceType())
                    .description(p.getDescription())
                    .fields(fieldsJson)
                    .createTime(new Date())
                    .updateTime(new Date())
                    .build();
        }).collect(Collectors.toList());
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
     * 把请求参数解析成执行/测试用的值，支持「按层级逐字段填、后端自动组装嵌套对象」：
     * <ul>
     *   <li>标量参数（或整包传入的对象）：直接取 {@code paramValue}；</li>
     *   <li>对象参数按 {@code fields} 逐字段填：递归把子字段组装成 {@link LinkedHashMap}，
     *       使脚本按 {@code input.<参数名>.<字段>} 即可取到嵌套结构；</li>
     *   <li>{@code paramValue} 优先于 {@code fields}（兼容整包传值的老前端）；</li>
     *   <li>缺值返回 {@code null}，由 {@link #buildInputMap} 省略该键，与各语言「缺值走脚本默认」一致。</li>
     * </ul>
     */
    static Object resolveParamValue(FunctionParameter p) {
        if (p.getParamValue() != null) {
            return p.getParamValue();
        }
        List<FunctionParameter> fields = p.getFields();
        if (CollectionUtils.isEmpty(fields)) {
            return null;
        }
        Map<String, Object> nested = new LinkedHashMap<>();
        for (FunctionParameter f : fields) {
            Object v = resolveParamValue(f);
            if (v != null) {
                nested.put(f.getParamName(), v);
            }
        }
        return nested.isEmpty() ? null : nested;
    }

    /**
     * 统一函数测试入口：根据函数类型分发到不同测试逻辑。
     * <p>支持两种模式：</p>
     * <ul>
     *   <li>已存库测试：functionApi 非空，按 api + version 查库取定义后执行</li>
     *   <li>草稿测试：functionApi 为空 + type 非空，直接用请求体中的 code / apiConfig 执行，不查库不落库</li>
     * </ul>
     */
    @SneakyThrows
    @Override
    public Object testFunction(FunctionTestParam param) {
        // 草稿测试：未落库的函数定义直接试跑
        if (param.isDraftTest()) {
            return testDraftFunction(param);
        }

        // 已存库测试：按 api + version 查库
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
                // 直接按原结构透传：CUSTOMIZE 保留脚本自己写的返回体，EXTERNAL/BASIC_QUERY/COMPOSITE 已不再包一层 VO
                var executeParam = FunctionExecuteParam.builder()
                        .functionApi(param.getFunctionApi())
                        .version(function.getVersion())
                        .parameters(param.getParameters())
                        .build();
                var resultJson = executeFunction(executeParam);
                return objectMapper.readValue(resultJson, new TypeReference<Map<String, Object>>() {
                });

            case CONTAINER:
                // 容器算子测试：复用 executeFunction（按 containerKey 调常驻容器，未运行则报错引导手动启动）
                var containerExecuteParam = FunctionExecuteParam.builder()
                        .functionApi(param.getFunctionApi())
                        .version(function.getVersion())
                        .parameters(param.getParameters())
                        .build();
                var containerResultJson = executeFunction(containerExecuteParam);
                return objectMapper.readValue(containerResultJson, new TypeReference<Map<String, Object>>() {
                });

            default:
                throw new BusinessException("不支持的函数类型：" + function.getType());
        }
    }

    /**
     * 草稿测试：未落库的函数定义直接试跑，不查库不落库。
     * <p>支持 CUSTOMIZE（脚本）和 EXTERNAL（外部API）两种类型。</p>
     */
    @SneakyThrows
    private Object testDraftFunction(FunctionTestParam param) {
        // 组装入参 Map
        Map<String, Object> inputMap = CollectionUtils.isEmpty(param.getParameters())
                ? new LinkedHashMap<>()
                : param.getParameters().stream().collect(LinkedHashMap::new,
                (m, p) -> {
                    Object v = resolveParamValue(p);
                    if (v != null) {
                        m.put(p.getParamName(), v);
                    }
                }, LinkedHashMap::putAll);

        FunctionTypeEnum type = param.getType();
        switch (type) {
            case CUSTOMIZE -> {
                PreconditionUtils.checkArgument(StringUtils.isNotEmpty(param.getCode()),
                        "脚本草稿测试必须提供 code", HttpStatus.BAD_REQUEST);
                FunctionLanguageEnum language = param.getLanguage() == null
                        ? FunctionLanguageEnum.GROOVY : param.getLanguage();
                // 草稿测试：单对象入参解包（与 executeFunction 语义对齐——组装结果为 Map 时才解包）
                String inputJson;
                Object singleVal = null;
                if (param.getParameters() != null && param.getParameters().size() == 1
                        && inputMap.containsKey(param.getParameters().get(0).getParamName())) {
                    singleVal = inputMap.get(param.getParameters().get(0).getParamName());
                }
                if (singleVal instanceof Map) {
                    inputJson = objectMapper.writeValueAsString(singleVal);
                } else {
                    inputJson = objectMapper.writeValueAsString(inputMap);
                }
                ScriptExecutionContext ctx = ScriptExecutionContext.builder()
                        .language(language)
                        .functionApi("__draft__")
                        .version("0.0.0")
                        .code(param.getCode())
                        .inputJson(inputJson)
                        .inputMap(inputMap)
                        .build();
                String resultJsonStr = scriptEngineRouter.route(language).execute(ctx);
                return objectMapper.readValue(resultJsonStr, new TypeReference<Map<String, Object>>() {
                });
            }
            case EXTERNAL -> {
                PreconditionUtils.checkArgument(param.getApiConfig() != null,
                        "外部API草稿测试必须提供 apiConfig", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(StringUtils.isNotBlank(param.getApiConfig().getEndpoint()),
                        "外部API草稿测试 apiConfig.endpoint 不能为空", HttpStatus.BAD_REQUEST);
                // 构造临时 Function 对象，不碰数据库
                String apiConfigJson = objectMapper.writeValueAsString(param.getApiConfig());
                Function tempFunction = Function.builder()
                        .api("__draft_external__")
                        .version("0.0.0")
                        .type(FunctionTypeEnum.EXTERNAL)
                        .code(apiConfigJson)
                        .build();
                Map<String, Object> ports = externalApiOperator.runAsNode(tempFunction, null, inputMap);
                return ports;
            }
            default -> throw new BusinessException("草稿测试仅支持 CUSTOMIZE / EXTERNAL 类型：" + type);
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
     * 执行函数编排：把画布配置编译为 LiteFlow 链并驱动执行，直接返回输出端口的数据体 JSON，
     * 不再额外包一层 {@code FunctionResultVO}。
     */
    private String executeCompositeFunction(Function function, Map<String, Object> funParamMap) throws JsonProcessingException {
        CompositeConfig config = parseCompositeConfig(function.getCode());
        PreconditionUtils.checkArgument(config != null, "函数编排定义为空：" + function.getApi(), HttpStatus.BAD_REQUEST);
        Map<String, Object> outputs = compositeChainRegistry.execute(function.getApi(), config, funParamMap);
        return objectMapper.writeValueAsString(outputs);
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
        return OperatorFunctionFactory.functionParamDefaults(
                        functionId, parameter.getName(), parameter.getType(), category, paramOrder, parameter.getDescription())
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
