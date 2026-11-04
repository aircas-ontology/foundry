package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.client.XxlJobClient;
import com.aircas.ptr.foundry.ontology.constant.FunctionConstant;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.enums.*;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.ObjectMapper;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OntologyActionServiceImpl extends ServiceImpl<OntologyActionMapper, OntologyAction> implements OntologyActionService {

    /** 属性值行为的主存储分组：main 行的增删改就是实体本身，由实体接口负责，不允许行为直接操作 */
    private static final String MAIN_STORAGE_GROUP = "main";

    /** 应用行为可绑定的算子类型：基础算子归入基础行为，函数编排不作为行为 */
    private static final List<FunctionTypeEnum> APPLICATION_FUNCTION_TYPES =
            Lists.newArrayList(FunctionTypeEnum.CUSTOMIZE, FunctionTypeEnum.EXTERNAL, FunctionTypeEnum.CONTAINER);


    @Resource
    private ActionHandleLogMapper actionHandleLogMapper;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private OntologyLinkGroupMapper linkGroupMapper;

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private FunctionService functionService;

    @Resource
    private FunctionParamMapper functionParamMapper;

    @Resource
    private OntologyActionLinkMapper actionLinkMapper;

    @Resource
    private OntologyActionMapper actionMapper;

    @Resource
    private ActionCategoryMapper actionCategoryMapper;

    @Resource
    private ObjectMapper entityMapper;

    @Resource
    private TableMetadataMapper tableMetadataMapper;

    @Resource
    private OntologyActionMappingInService ontologyActionMappingInService;

    @Lazy
    @Resource
    private OntologyPropertyService ontologyPropertyService;

    @Resource
    private ActionHandleRuleService actionHandleRuleService;

    @Resource
    private ActionHandleTaskService actionHandleTaskService;


    @Lazy
    @Resource
    private EntityServiceImpl entityService;

    @Resource
    private XxlJobClient xxlJobClient;


    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public void removeByOntologyIdentifier(String ontologyIdentifier) {
        var actions = list(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, ontologyIdentifier));
        if (CollectionUtils.isNotEmpty(actions)) {
            var actionIds = actions.stream().map(v -> v.getId()).collect(Collectors.toList());
            actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().in(OntologyActionLink::getOntologyActionId, actionIds));
            remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, actionIds));
            ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, actionIds));
            actionHandleRuleService.remove(new LambdaQueryWrapper<ActionHandleRule>().in(ActionHandleRule::getActionId, actionIds));
            //删除定时调度任务
            var tasks = actionHandleTaskService.list(new LambdaQueryWrapper<ActionHandleTask>().in(ActionHandleTask::getActionId, actionIds));
            tasks.forEach(t -> removeScheduling(ActionSchedulingTypeEnum.TASK, t.getId()));
            actionHandleTaskService.remove(new LambdaQueryWrapper<ActionHandleTask>().in(ActionHandleTask::getActionId, actionIds));
        }
    }


    @Override
    @Transactional(value = "mainTransactionManager")
    public void createAction(ActionCreateOrUpdateParam param) {
        // 应用行为可不传 ontologyIdentifier，从分类取空间 id
        Integer spaceId = null;
        if (StringUtils.isNotEmpty(param.getOntologyIdentifier())) {
            var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
            PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
            spaceId = meta.getOntologySpaceId();
        } else {
            var category = actionCategoryMapper.selectById(param.getCategoryId());
            PreconditionUtils.checkNotNull(category, "无效的行为分类：" + param.getCategoryId(), HttpStatus.BAD_REQUEST);
            spaceId = category.getOntologySpaceId();
        }
        var exist = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(exist == null, "action api 已存在：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        var action = new OntologyAction();
        // 新建落草稿：需要显式发布才对外可用（行为测试不受此限制，草稿正是拿来试跑的）
        action.setStatus(ActionStatusEnum.DRAFT.getValue());
        var definition = checkActionDefinition(action, param, spaceId);
        save(action);
        saveActionChildren(action.getId(), definition);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateAction(ActionCreateOrUpdateParam param) {
        // 应用行为可不传 ontologyIdentifier，从分类取空间 id
        Integer spaceId = null;
        if (StringUtils.isNotEmpty(param.getOntologyIdentifier())) {
            var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
            PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
            spaceId = meta.getOntologySpaceId();
        } else {
            var category = actionCategoryMapper.selectById(param.getCategoryId());
            PreconditionUtils.checkNotNull(category, "无效的行为分类：" + param.getCategoryId(), HttpStatus.BAD_REQUEST);
            spaceId = category.getOntologySpaceId();
        }
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, spaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        // 编辑不改状态：草稿还是草稿、已发布还是已发布
        var originalStatus = action.getStatus();
        var definition = checkActionDefinition(action, param, spaceId);
        action.setStatus(originalStatus);
        // 原地更新：行为 id 被关联关系、参数绑定和调度任务引用，删除重建会让它们失去引用
        updateById(action);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getOntologyActionId, action.getId()));
        saveActionChildren(action.getId(), definition);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void publishAction(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        checkBoundFunctionPublished(action);
        action.setStatus(ActionStatusEnum.PUBLISHED.getValue());
        updateById(action);
    }

    /** 绑算子的行为发布后要按引用执行，算子还停在草稿时执行必失败，所以发布前先看算子发布没有 */
    private void checkBoundFunctionPublished(OntologyAction action) {
        if (StringUtils.isEmpty(action.getFunctionApi())) {
            return;
        }
        // 与行为定义校验同源：算子 api 只在空间内唯一，不加空间条件会命中其他空间的同名算子；
        // 空间列为空的早期行为没有可比的归属空间，只能退回按 api 找
        var wrapper = new LambdaQueryWrapper<Function>().eq(Function::getApi, action.getFunctionApi());
        if (action.getOntologySpaceId() != null) {
            wrapper.eq(Function::getOntologySpaceId, action.getOntologySpaceId());
        }
        var func = functionMapper.selectList(wrapper)
                .stream()
                .max(Comparator.comparing((Function f) -> f.getVersion() == null ? "" : f.getVersion(),
                        FunctionServiceImpl::compareVersion)
                        .thenComparing(Function::getId))
                .orElse(null);
        PreconditionUtils.checkArgument(func != null, "行为绑定的算子不存在：" + action.getFunctionApi(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(Objects.equals(func.getPublish(), FunctionConstant.PUBLISH_PUBLISHED),
                "算子[" + action.getFunctionApi() + "]尚未发布，请先发布算子再发布行为", HttpStatus.BAD_REQUEST);
    }

    /**
     * 校验行为定义：校验通过的类别写入 action，待落库的关联关系与参数绑定收集进返回值。
     */
    private ActionDefinition checkActionDefinition(OntologyAction action, ActionCreateOrUpdateParam param, Integer spaceId) {
        // 归属的行为分类必须存在，且与对象同属一个对象空间
        var category = actionCategoryMapper.selectById(param.getCategoryId());
        PreconditionUtils.checkArgument(category != null, "无效的行为分类：" + param.getCategoryId(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(category.getOntologySpaceId().equals(spaceId),
                "行为分类不属于该对象所在的空间：" + category.getName(), HttpStatus.BAD_REQUEST);

        // 类别、输出方式必须由调用方明确传入：猜错会把行为建成另一种，执行时才暴露很难定位
        PreconditionUtils.checkNotNull(param.getActionKind(), "必须选择行为类别：基础行为/应用行为", HttpStatus.BAD_REQUEST);
        var kind = param.getActionKind();
        var definition = new ActionDefinition();
        if (kind == ActionKindEnum.APPLICATION) {
            PreconditionUtils.checkArgument(param.getSubjectType() == null && param.getOperation() == null
                    && StringUtils.isEmpty(param.getStorageGroup()), "应用行为不需要行为类型、基础操作和存储分组", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkNotNull(param.getOutputMode(), "应用行为必须选择输出方式", HttpStatus.BAD_REQUEST);
            checkFunctionAction(param, definition, APPLICATION_FUNCTION_TYPES, true, spaceId);
            if (param.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY) {
                checkWritePropertyOutput(definition);
            }
            action.setOutputMode(param.getOutputMode());
        } else {
            var subjectType = param.getSubjectType();
            if (subjectType == null) {
                throw new BusinessException("基础行为必须选择行为类型", HttpStatus.BAD_REQUEST);
            }
            if (subjectType == ActionSubjectTypeEnum.PROPERTY_VALUE) {
                checkPropertyValueAction(param, definition);
            } else if (subjectType == ActionSubjectTypeEnum.LINK) {
                checkLinkAction(param, definition);
            } else {
                PreconditionUtils.checkArgument(param.getOperation() == null && StringUtils.isEmpty(param.getStorageGroup()),
                        "基础算子行为不需要基础操作和存储分组", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(param.getOutputMode() != ActionOutputModeEnum.WRITE_PROPERTY,
                        "基础算子行为只支持仅返回结果", HttpStatus.BAD_REQUEST);
                checkFunctionAction(param, definition, Collections.singletonList(FunctionTypeEnum.BASIC_QUERY),
                        false, spaceId);
            }
            // 属性值、实例关系的输出即落库本身，没有回写方式；基础算子只读
            action.setOutputMode(subjectType == ActionSubjectTypeEnum.BASIC_OPERATOR
                    ? ActionOutputModeEnum.RESULT_ONLY : null);
        }

        action.setApi(param.getActionApi());
        action.setDisplayName(param.getDisplayName());
        action.setDescription(param.getDescription());
        action.setIcon(param.getIcon());
        action.setFunctionApi(param.getFunctionApi());
        action.setOntologyUniqueIdentifier(param.getOntologyIdentifier());
        action.setOntologySpaceId(spaceId);
        action.setActionCategoryId(param.getCategoryId());
        action.setActionKind(kind);
        action.setSubjectType(param.getSubjectType());
        action.setActionOperation(param.getOperation());
        action.setStorageGroup(param.getStorageGroup());
        return definition;
    }

    /**
     * 绑定算子的行为（应用行为、基础算子行为）：校验算子类型、关联关系和入参绑定。
     *
     * @param allowedTypes 该类别允许的算子类型
     * @param linkRequired 应用行为必须绑定一条关系，执行时据此补一条实例关系
     * @param spaceId      行为所属空间，算子 api 仅在该空间内唯一
     */
    private void checkFunctionAction(ActionCreateOrUpdateParam param, ActionDefinition definition,
                                     List<FunctionTypeEnum> allowedTypes, boolean linkRequired, Integer spaceId) {
        var funcApi = param.getFunctionApi();
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(funcApi), "行为必须绑定算子", HttpStatus.BAD_REQUEST);
        // 算子 api 只保证空间内唯一（跨空间允许重名），取最新版本必须先限定在本空间，否则会选中其他空间的同名算子
        definition.func = functionMapper.selectList(new LambdaQueryWrapper<Function>()
                        .eq(Function::getOntologySpaceId, spaceId)
                        .eq(Function::getApi, funcApi))
                .stream()
                .max(Comparator.comparing((Function f) -> f.getVersion() == null ? "" : f.getVersion(),
                                FunctionServiceImpl::compareVersion)
                        .thenComparing(Function::getId))
                .orElse(null);
        PreconditionUtils.checkArgument(definition.func != null && allowedTypes.contains(definition.func.getType()),
                "function api 不存在或算子类型不匹配：" + funcApi, HttpStatus.BAD_REQUEST);
        definition.funcParams = functionParamMapper.selectList(new LambdaQueryWrapper<FunctionParamPO>()
                .eq(FunctionParamPO::getFunctionId, definition.func.getId()));
        checkActionLink(param, definition, linkRequired);

        var paramMap = definition.funcParams.stream()
                .collect(Collectors.toMap(FunctionParamPO::getId, v -> v, (existingValue, newValue) -> existingValue));
        if (CollectionUtils.isEmpty(param.getMappingIns())) {
            return;
        }
        for (var mapping : param.getMappingIns()) {
            var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.PROPERTY : mapping.getSourceType();
            OntologyProperty property = null;
            if (StringUtils.isNotEmpty(mapping.getPropertyUniqueIdentifier())) {
                property = ontologyPropertyService.getOne(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getUniqueIdentifier, mapping.getPropertyUniqueIdentifier()));
            }
            // 固定值和外部输入没有来源属性，其余来源必须指向一个真实属性
            var needProperty = sourceType != ActionParamSourceEnum.FIXED && sourceType != ActionParamSourceEnum.EXTERNAL;
            PreconditionUtils.checkArgument(!needProperty || property != null,
                    "无效的行为参数：" + mapping.getPropertyUniqueIdentifier(), HttpStatus.BAD_REQUEST);
            var funcParam = paramMap.get(mapping.getFunctionParamId());
            PreconditionUtils.checkArgument(funcParam != null, "无效的函数参数：" + mapping.getFunctionParamId(), HttpStatus.BAD_REQUEST);
            var paramPath = normalizeParamPath(funcParam, mapping.getParamPath());
            if (property != null && funcParam.getCategory().equals(FunctionParamCategoryEnum.INPUT)) {
                var dataType = OntologyDataTypeEnum.valueOfDataType(resolveInputParamType(funcParam, paramPath));
                // 对象/自定义类参数没有对应的属性数据类型，整包绑定时不做标量比对
                PreconditionUtils.checkArgument(dataType == null || dataType.equals(OntologyDataTypeEnum.Array)
                                || property.getPropertyType().equals(dataType),
                        "行为属性类型与函数参数类型不匹配：", HttpStatus.BAD_REQUEST);
            }
            // 出参写回落库时只在本对象自己的属性表里找列，绑到别的对象上执行时会取不到属性，必须在这里挡住
            if (funcParam.getCategory().equals(FunctionParamCategoryEnum.OUTPUT)
                    && param.getOutputMode() == ActionOutputModeEnum.WRITE_PROPERTY) {
                PreconditionUtils.checkArgument(property != null
                                && param.getOntologyIdentifier().equals(property.getOntologyUniqueIdentifier()),
                        "出参写回的属性必须属于该行为所属的对象：" + mapping.getPropertyUniqueIdentifier(), HttpStatus.BAD_REQUEST);
            }
            definition.mappings.add(OntologyActionMappingIn.builder()
                    .functionParamId(funcParam.getId())
                    .functionParamExpression(mapping.getFunctionParamExpression())
                    .parameterName(StringUtils.isEmpty(mapping.getParameterName()) ? funcParam.getParamName() : mapping.getParameterName())
                    .propertyUniqueIdentifier(mapping.getPropertyUniqueIdentifier())
                    .sourceType(sourceType)
                    .sourceConfig(mapping.getSourceConfig())
                    .paramPath(paramPath)
                    .build());
        }
    }

    /** 子字段路径归一：去掉前端可能带上的参数名前缀，空白按「未绑定子字段」处理 */
    private String normalizeParamPath(FunctionParamPO funcParam, String paramPath) {
        if (StringUtils.isBlank(paramPath)) {
            return null;
        }
        return StringUtils.removeStart(paramPath.trim(), funcParam.getParamName() + ".");
    }

    /**
     * 取一条入参绑定实际对应的声明类型。
     * <p>
     * 对象（自定义类）参数在 function_param 表里只有一行，子字段整体存在该行的 fields JSON 中、
     * 没有自己的参数 id，所以只能按已归一的子字段路径在字段树里逐层定位。
     */
    private FunctionParamTypeEnum resolveInputParamType(FunctionParamPO funcParam, String paramPath) {
        if (StringUtils.isEmpty(paramPath)) {
            return funcParam.getParamType();
        }
        var fields = parseParamFields(funcParam);
        FunctionParamTypeEnum currentType = funcParam.getParamType();
        for (var segment : paramPath.split("\\.")) {
            var field = fields.stream().filter(f -> segment.equals(f.getParamName())).findFirst().orElse(null);
            PreconditionUtils.checkArgument(field != null,
                    "算子入参 " + funcParam.getParamName() + " 不存在子字段：" + paramPath, HttpStatus.BAD_REQUEST);
            currentType = field.getParamType();
            fields = field.getFields() == null ? Lists.newArrayList() : field.getFields();
        }
        return currentType;
    }

    /** function_param.fields 存的是 {@link FunctionParameterVO} 列表 JSON，解析不了说明算子参数定义本身已损坏，直接抛出 */
    @SneakyThrows
    private List<FunctionParameterVO> parseParamFields(FunctionParamPO funcParam) {
        if (StringUtils.isEmpty(funcParam.getFields())) {
            return Lists.newArrayList();
        }
        return jsonMapper.readValue(funcParam.getFields(), new TypeReference<List<FunctionParameterVO>>() {
        });
    }

    /** 写入对象属性要求算子有出参，且定义里至少把一个出参绑到了属性 */
    private void checkWritePropertyOutput(ActionDefinition definition) {
        var outputParamIds = definition.funcParams.stream()
                .filter(p -> p.getCategory().equals(FunctionParamCategoryEnum.OUTPUT))
                .map(FunctionParamPO::getId)
                .collect(Collectors.toSet());
        PreconditionUtils.checkArgument(!outputParamIds.isEmpty(),
                "算子没有输出参数，无法写入对象属性：" + definition.func.getApi(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(definition.mappings.stream().anyMatch(m -> outputParamIds.contains(m.getFunctionParamId())),
                "输出方式为写入对象属性时，至少要绑定一个输出参数", HttpStatus.BAD_REQUEST);
    }

    /** 属性值行为：对某个存储分组（物理表）做新增/修改/删除，不绑算子 */
    private void checkPropertyValueAction(ActionCreateOrUpdateParam param, ActionDefinition definition) {
        PreconditionUtils.checkArgument(StringUtils.isEmpty(param.getFunctionApi()), "属性值行为不需要绑定算子", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getLinkMapping() == null
                        || StringUtils.isEmpty(param.getLinkMapping().getOntologyLinkUniqIdentifier()),
                "属性值行为不需要关联关系", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkNotNull(param.getOperation(), "属性值行为必须选择基础操作", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(param.getStorageGroup()), "属性值行为必须选择存储分组", HttpStatus.BAD_REQUEST);
        // main 分组只支持修改，不支持新增和删除（新增/删除走实体接口）
        if (MAIN_STORAGE_GROUP.equals(param.getStorageGroup())) {
            PreconditionUtils.checkArgument(param.getOperation() == ActionOperationEnum.UPDATE,
                    "属性值行为的 main 分组只支持修改，不支持新增和删除", HttpStatus.BAD_REQUEST);
        }

        var groupPropMap = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, param.getOntologyIdentifier()))
                .stream()
                .filter(p -> param.getStorageGroup().equals(p.getStorageGroup()))
                .collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, v -> v, (existingValue, newValue) -> existingValue));
        PreconditionUtils.checkArgument(!groupPropMap.isEmpty(), "该对象不存在存储分组：" + param.getStorageGroup(), HttpStatus.BAD_REQUEST);
        // 主键由分组表自己的序列生成，不参与绑定
        var bindableProps = groupPropMap.entrySet().stream()
                .filter(entry -> entry.getValue().getIsPrimaryKey() == 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        checkPropertyMappings(param, definition, bindableProps);
        if (param.getOperation() == ActionOperationEnum.CREATE) {
            // 新增要求分组下属性全部绑定，漏一个就会写出结构不完整的行
            var boundIds = definition.mappings.stream()
                    .map(OntologyActionMappingIn::getPropertyUniqueIdentifier)
                    .collect(Collectors.toSet());
            var missing = bindableProps.entrySet().stream()
                    .filter(entry -> !boundIds.contains(entry.getKey()))
                    .map(entry -> entry.getValue().getDisplayName())
                    .collect(Collectors.joining("、"));
            PreconditionUtils.checkArgument(StringUtils.isEmpty(missing),
                    "属性值-新增必须绑定分组[" + param.getStorageGroup() + "]下的全部属性，缺少：" + missing, HttpStatus.BAD_REQUEST);
        }
    }

    /** 不绑算子的行为：每个参数行对应一个目标属性，取值来源缺省按外部输入 */
    private void checkPropertyMappings(ActionCreateOrUpdateParam param, ActionDefinition definition,
                                       Map<String, OntologyProperty> bindableProps) {
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(param.getMappingIns()), "请选择要操作的属性", HttpStatus.BAD_REQUEST);
        var boundIds = new HashSet<String>();
        for (var mapping : param.getMappingIns()) {
            var propertyId = mapping.getPropertyUniqueIdentifier();
            var property = bindableProps.get(propertyId);
            PreconditionUtils.checkNotNull(property, "无效的属性：" + propertyId, HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(boundIds.add(propertyId), "属性重复绑定：" + propertyId, HttpStatus.BAD_REQUEST);
            var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.EXTERNAL : mapping.getSourceType();
            // 对象/List 来源是把值组装进算子入参，属性值行为没有算子，定义存下来也无处取值
            PreconditionUtils.checkArgument(sourceType != ActionParamSourceEnum.OBJECT && sourceType != ActionParamSourceEnum.LIST,
                    "属性值行为的取值来源不支持" + sourceType.getName() + "，请用对象属性、固定值或外部输入：" + property.getDisplayName(),
                    HttpStatus.BAD_REQUEST);
            definition.mappings.add(OntologyActionMappingIn.builder()
                    .parameterName(StringUtils.isEmpty(mapping.getParameterName()) ? property.getApiName() : mapping.getParameterName())
                    .propertyUniqueIdentifier(propertyId)
                    .sourceType(sourceType)
                    .sourceConfig(mapping.getSourceConfig())
                    .functionParamExpression(mapping.getFunctionParamExpression())
                    .build());
        }
    }

    /** 实例关系行为：对某条关系做新增/修改/删除，不绑算子；修改的目标状态在行为定义里固定 */
    private void checkLinkAction(ActionCreateOrUpdateParam param, ActionDefinition definition) {
        PreconditionUtils.checkArgument(StringUtils.isEmpty(param.getFunctionApi()), "实例关系行为不需要绑定算子", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(StringUtils.isEmpty(param.getStorageGroup()), "实例关系行为不需要存储分组", HttpStatus.BAD_REQUEST);
        var operation = param.getOperation();
        PreconditionUtils.checkNotNull(operation, "实例关系行为必须选择基础操作", HttpStatus.BAD_REQUEST);
        checkActionLink(param, definition, true);
        var visibilityWindows = param.getLinkMapping().getVisibilityWindows();
        if (operation == ActionOperationEnum.DELETE) {
            // 删除只是把边置为删除态，状态和时段都不取定义里的值
            definition.actionLink.setTargetStatus(null);
            definition.actionLink.setTimeWindows(null);
        } else {
            checkVisibilityWindows(visibilityWindows);
            var targetStatus = definition.actionLink.getTargetStatus();
            if (operation == ActionOperationEnum.UPDATE) {
                PreconditionUtils.checkArgument(targetStatus != null && targetStatus != Status.DELETE,
                        "实例关系-修改必须指定目标状态（启用/停用）", HttpStatus.BAD_REQUEST);
            } else {
                // 新增允许直接建成删除态的边：删除态只是不生效，不是消失，语义交给使用者定
                PreconditionUtils.checkNotNull(targetStatus, "实例关系-新增必须指定目标状态", HttpStatus.BAD_REQUEST);
                PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(visibilityWindows),
                        "实例关系-新增必须填写持续时段", HttpStatus.BAD_REQUEST);
            }
        }
        // 实例关系行为只按定义里的目标状态与持续时段改边，边上没有可写参数值的地方，创建时就不收
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(param.getMappingIns()),
                "实例关系行为不支持绑定参数，请只配置状态与持续时段", HttpStatus.BAD_REQUEST);
    }

    /**
     * 校验并收集行为关联的关系。
     * <p>
     * 同一条关系可以被多个行为关联（新增、修改、删除各是一个行为），不再限制一关系一行为。
     */
    private void checkActionLink(ActionCreateOrUpdateParam param, ActionDefinition definition, boolean required) {
        var linkMapping = param.getLinkMapping();
        var linkId = linkMapping == null ? null : linkMapping.getOntologyLinkUniqIdentifier();
        if (StringUtils.isEmpty(linkId)) {
            PreconditionUtils.checkArgument(!required, "该行为必须绑定一条对象关系", HttpStatus.BAD_REQUEST);
            return;
        }
        var ontologyId = param.getOntologyIdentifier();
        var link = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkId));
        PreconditionUtils.checkArgument(link != null && (ontologyId.equals(link.getOntologyUniqueIdentifierFrom())
                        || ontologyId.equals(link.getOntologyUniqueIdentifierTo())),
                "无效的对象关系：" + linkId, HttpStatus.BAD_REQUEST);
        definition.actionLink = OntologyActionLink.builder()
                .ontologyLinkUniqueIdentifier(linkId)
                .ontologyLinkParamExpression(linkMapping.getOntologyLinkFunctionParamExpression())
                .targetStatus(linkMapping.getTargetStatus())
                .timeWindows(writeTimeWindows(linkMapping.getVisibilityWindows()))
                .build();
    }

    /** 持续时段按 JSON 存在 ontology_action_link.time_windows，空列表与不填等价 */
    @SneakyThrows
    private String writeTimeWindows(List<VisibilityWindow> windows) {
        return CollectionUtils.isEmpty(windows) ? null : jsonMapper.writeValueAsString(windows);
    }

    /** 读回行为定义里的持续时段，未配置时返回空列表 */
    @SneakyThrows
    private List<VisibilityWindow> readTimeWindows(String timeWindows) {
        if (StringUtils.isEmpty(timeWindows)) {
            return Lists.newArrayList();
        }
        return jsonMapper.readValue(timeWindows, new TypeReference<List<VisibilityWindow>>() {
        });
    }

    /** 时段执行时原样写到图库的边上，要求每段起止齐全且结束晚于开始 */
    private void checkVisibilityWindows(List<VisibilityWindow> windows) {
        if (CollectionUtils.isEmpty(windows)) {
            return;
        }
        var invalid = windows.stream().anyMatch(w -> w.getStartTime() == null || w.getEndTime() == null
                || !w.getEndTime().after(w.getStartTime()));
        PreconditionUtils.checkArgument(!invalid, "持续时段需填写开始与结束时间，且结束时间晚于开始时间", HttpStatus.BAD_REQUEST);
    }

    private void saveActionChildren(Long actionId, ActionDefinition definition) {
        if (definition.actionLink != null) {
            definition.actionLink.setOntologyActionId(actionId);
            actionLinkMapper.insert(definition.actionLink);
        }
        if (CollectionUtils.isNotEmpty(definition.mappings)) {
            definition.mappings.forEach(mapping -> mapping.setOntologyActionId(actionId));
            ontologyActionMappingInService.saveBatch(definition.mappings);
        }
    }

    /**
     * 行为定义校验的产物：待落库的关联关系、参数绑定，以及本次绑定的算子上下文。
     */
    private static class ActionDefinition {
        private Function func;
        private List<FunctionParamPO> funcParams = new ArrayList<>();
        private OntologyActionLink actionLink;
        private final List<OntologyActionMappingIn> mappings = new ArrayList<>();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteActionByApi(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        var actionHandleRule = actionHandleRuleService.getOne(new LambdaQueryWrapper<ActionHandleRule>()
                .eq(ActionHandleRule::getActionId, action.getId()));
        var actionHandleTask = actionHandleTaskService.getOne(new LambdaQueryWrapper<ActionHandleTask>()
                .eq(ActionHandleTask::getActionId, action.getId()));
        PreconditionUtils.checkArgument(actionHandleRule == null && actionHandleTask == null, "该行为在调度中，不能直接删除", HttpStatus.BAD_REQUEST);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, action.getId()));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, action.getId()));
    }

    @Override
    public Page<OntologyActionInfoVO> pageGetActionByOntologyId(Integer ontologySpaceId, String ontologyId, Integer categoryId, String keyword, Integer status, Integer pageNum, Integer pageSize) {
        Page<OntologyActionInfoVO> result = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<OntologyAction> wrapper = new LambdaQueryWrapper<>();
        if (ontologySpaceId != null) {
            wrapper.eq(OntologyAction::getOntologySpaceId, ontologySpaceId);
        }
        if (StringUtils.isNotEmpty(ontologyId)) {
            wrapper.eq(OntologyAction::getOntologyUniqueIdentifier, ontologyId);
        }
        if (categoryId != null) {
            wrapper.eq(OntologyAction::getActionCategoryId, categoryId);
        }
        // 关键词：行为名称、算子 api、描述
        if (StringUtils.isNotEmpty(keyword)) {
            wrapper.and(w -> w.like(OntologyAction::getDisplayName, keyword)
                    .or().like(OntologyAction::getFunctionApi, keyword)
                    .or().like(OntologyAction::getDescription, keyword));
        }
        // 状态过滤
        if (status != null) {
            wrapper.eq(OntologyAction::getStatus, status);
        }
        // 最新创建的排在前面。按自增主键倒序而不是 create_time 倒序：
        // 一是等价于创建顺序，二是 create_time 允许为空，PostgreSQL 的 DESC 默认 NULLS FIRST 会把老数据顶到最前
        wrapper.orderByDesc(OntologyAction::getId);
        var pageResult = page(new Page<>(pageNum, pageSize), wrapper);
        // 算子 api 只在空间内唯一，描述必须按行为所在空间取；不传空间筛选时一页可能跨多个空间，按空间分组各查一次。
        // 早期行为的空间列可能为空，用 HashMap 手动分组（groupingBy 遇到 null 键会抛 NPE）
        var apisBySpace = new HashMap<Integer, List<String>>();
        for (var action : pageResult.getRecords()) {
            if (StringUtils.isNotEmpty(action.getFunctionApi())) {
                apisBySpace.computeIfAbsent(action.getOntologySpaceId(), k -> new ArrayList<>()).add(action.getFunctionApi());
            }
        }
        var functionDescMap = new HashMap<String, String>();
        apisBySpace.forEach((spaceId, apis) -> functionService.mapDescriptionByApi(spaceId, apis)
                .forEach((api, desc) -> functionDescMap.put(functionDescKey(spaceId, api), desc)));
        List<OntologyActionInfoVO> records = pageResult.getRecords().stream()
                .map(v -> OntologyActionInfoVO.builder()
                        .id(v.getId())
                        .actionApi(v.getApi())
                        .description(v.getDescription())
                        .ontologyUniqIdentifier(v.getOntologyUniqueIdentifier())
                        .displayName(v.getDisplayName())
                        .icon(v.getIcon())
                        .functionApi(v.getFunctionApi())
                        .functionDescription(functionDescMap.get(functionDescKey(v.getOntologySpaceId(), v.getFunctionApi())))
                        .categoryId(v.getActionCategoryId())
                        .status(v.getStatus())
                        .actionKind(v.getActionKind())
                        .subjectType(v.getSubjectType())
                        .operation(v.getActionOperation())
                        .storageGroup(v.getStorageGroup())
                        .outputMode(v.getOutputMode())
                        .createTime(v.getCreateTime())
                        .updateTime(v.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
        result.setRecords(records).setTotal(pageResult.getTotal());
        return result;
    }

    /** 算子描述按「空间 + api」定位，跨空间的同名 api 各是各的算子 */
    private String functionDescKey(Integer spaceId, String functionApi) {
        return spaceId + ":" + functionApi;
    }

    @Override
    public OntologyActionDetailVO getActionByApi(Integer ontologySpaceId, String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        var detailVO = OntologyActionDetailVO
                .builder()
                .id(action.getId())
                .ontologySpaceId(action.getOntologySpaceId())
                .functionApi(action.getFunctionApi())
                .functionDescription(functionService
                        .mapDescriptionByApi(action.getOntologySpaceId(), Collections.singletonList(action.getFunctionApi()))
                        .get(action.getFunctionApi()))
                .actionApi(action.getApi())
                .description(action.getDescription())
                .displayName(action.getDisplayName())
                .ontologyUniqIdentifier(action.getOntologyUniqueIdentifier())
                .icon(action.getIcon())
                .categoryId(action.getActionCategoryId())
                .status(action.getStatus())
                .actionKind(action.getActionKind())
                .subjectType(action.getSubjectType())
                .operation(action.getActionOperation())
                .storageGroup(action.getStorageGroup())
                .outputMode(action.getOutputMode())
                .createTime(action.getCreateTime())
                .updateTime(action.getUpdateTime())
                .build();
        var link = actionLinkMapper.selectOne(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        if (link != null) {
            detailVO.setLinkMapping(ActionLinkMappingParam.builder()
                    .ontologyLinkFunctionParamExpression(link.getOntologyLinkParamExpression())
                    .ontologyLinkUniqIdentifier(link.getOntologyLinkUniqueIdentifier())
                    .targetStatus(link.getTargetStatus())
                    .visibilityWindows(readTimeWindows(link.getTimeWindows()))
                    .build());
        }
        var mappings = ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getOntologyActionId, action.getId()));

        if (CollectionUtils.isNotEmpty(mappings)) {

            var propUniqIds = mappings.stream().map(v -> v.getPropertyUniqueIdentifier()).collect(Collectors.toList());
            var props = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>().in(OntologyProperty::getUniqueIdentifier, propUniqIds));
            var propMap = props.stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v.getOntologyUniqueIdentifier()));

            var mappingVOS = mappings.stream().map(v -> ActionParamMappingVO.builder()
                            .functionParamExpression(v.getFunctionParamExpression())
                            .functionParamId(v.getFunctionParamId())
                            .parameterName(v.getParameterName())
                            .sourceType(v.getSourceType())
                            .sourceConfig(v.getSourceConfig())
                            .paramPath(v.getParamPath())
                            .propertyUniqueIdentifier(v.getPropertyUniqueIdentifier())
                            .ontologyUniqueIdentifier(propMap.get(v.getPropertyUniqueIdentifier()))
                            .build())
                    .collect(Collectors.toList());
            detailVO.setMappingIns(mappingVOS);
        }
        // 函数参数定义（入参 + 出参），前端绑定 UI 用；算子 api 跨空间允许重名，必须按本行为所属空间取
        if (StringUtils.isNotEmpty(action.getFunctionApi())) {
            var func = functionMapper.selectList(new LambdaQueryWrapper<Function>()
                            .eq(Function::getOntologySpaceId, action.getOntologySpaceId())
                            .eq(Function::getApi, action.getFunctionApi()))
                    .stream()
                    .max(Comparator.comparing((Function f) -> f.getVersion() == null ? "" : f.getVersion(),
                                    FunctionServiceImpl::compareVersion)
                            .thenComparing(Function::getId))
                    .orElse(null);
            if (func != null) {
                var funcParams = functionParamMapper.selectList(new LambdaQueryWrapper<FunctionParamPO>()
                        .eq(FunctionParamPO::getFunctionId, func.getId()));
                detailVO.setFunctionParams(funcParams.stream().map(p -> FunctionParamVO.builder()
                        .paramId(p.getId())
                        .paramName(p.getParamName())
                        .paramType(p.getParamType())
                        .category(p.getCategory())
                        .paramOrder(p.getParamOrder())
                        .description(p.getDescription())
                        .referenceType(p.getTypeReferenceName())
                        .build()).collect(Collectors.toList()));
            }
        }
        return detailVO;
    }

    @SneakyThrows
    @Override
    public String executeAction(OntologyActionExecuteParam param) {
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyUniqueIdentifier()));
        PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyUniqueIdentifier(), HttpStatus.BAD_REQUEST);

        var contextInfoDTO = entityService.initActionContextInfoDTO(EntityActionExecuteParam.builder()
                .actionApi(param.getActionApi())
                .ontologySpaceId(meta.getOntologySpaceId())
                .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                .build());
        // 校验行为属于该空间
        PreconditionUtils.checkArgument(contextInfoDTO.getActionDetailVO() != null
                && meta.getOntologySpaceId().equals(contextInfoDTO.getActionDetailVO().getOntologySpaceId()),
                "行为不属于该对象所在的空间", HttpStatus.BAD_REQUEST);
        checkBulkExecutable(contextInfoDTO.getActionDetailVO());
        //获取实体主键列表
        var primaryProperty = contextInfoDTO.getOntologyProperties().stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst().orElse(null);
        //主键数据源未绑定
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceColumnName())) {
            return "";
        }
        //查询对象的实体主键
        var hasDeletedField = tableMetadataMapper.isColumnExist(primaryProperty.getDatasourceSchema(), primaryProperty.getDatasourceId(), "is_deleted");
        var records = entityMapper.pageQuery(
                primaryProperty.getDatasourceSchema(),
                primaryProperty.getDatasourceId(),
                Lists.newArrayList(primaryProperty.getDatasourceColumnName()),
                null,
                null,
                Integer.MAX_VALUE,
                0,
                hasDeletedField);
        //执行每个实体的行为
        //获取行为关联关系下的对象的所有实体详情
        List<List<EntityPropertyDetailVO>> linkedEntities = entityService.getLinkedEntities(contextInfoDTO);
        //执行日志
        List<String> logMsg = Lists.newArrayList();
        var shardTotal = param.getShardTotal();
        var shardIndex = param.getShardIndex();
        for (int i = 0; i < records.size(); i++) {
            if ((shardIndex == null || shardIndex == null)
                    || (i % shardTotal == shardIndex)) {
                var record = records.get(i);
                var entityPrimaryKey = record.get(primaryProperty.getDatasourceColumnName());
                var entityActionExecuteParam = EntityActionExecuteParam.builder()
                        .actionApi(param.getActionApi())
                        .ontologySpaceId(meta.getOntologySpaceId())
                        .entityPrimaryKey(entityPrimaryKey)
                        .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                        .build();
                try {
                    entityService.executeEntityAction(entityActionExecuteParam, contextInfoDTO, linkedEntities);
                    var msg = "执行行为" + param.getActionApi() + "成功, OntologyUniqueIdentifier:" + param.getOntologyUniqueIdentifier() + ", entityPrimaryKey:" + entityPrimaryKey;
                    logMsg.add(msg);
                    log.info(msg);
                    XxlJobHelper.log(msg);
                } catch (Exception e) {
                    var errMsg = "执行行为" + param.getActionApi() + "失败, OntologyUniqueIdentifier:" + param.getOntologyUniqueIdentifier() + ", entityPrimaryKey:" + entityPrimaryKey;
                    log.error(errMsg, e);
                    XxlJobHelper.log(errMsg);
                    logMsg.add(errMsg);
                }
            }
        }
        return jsonMapper.writeValueAsString(logMsg);
    }

    /**
     * 按对象下全部实例批量执行时，每个实例只带得出实体主键，
     * 因此只放行取值不依赖分组数据主键的属性值操作；实例关系行为还必须指定对端实体。
     */
    private void checkBulkExecutable(OntologyActionDetailVO detail) {
        if (StringUtils.isNotEmpty(detail.getFunctionApi())) {
            return;
        }
        if (detail.getSubjectType() == ActionSubjectTypeEnum.LINK) {
            throw new BusinessException("实例关系行为要指定对端实体，不支持按对象下全部实例执行，请用单实体执行或行为测试："
                    + detail.getActionApi(), HttpStatus.BAD_REQUEST);
        }
        var operation = detail.getOperation();
        // main 分组的行主键就是实体主键，批量执行能定位到行；非 main 分组的修改和删除都需要分组数据主键
        var determinable = operation == ActionOperationEnum.CREATE
                || (operation == ActionOperationEnum.UPDATE && MAIN_STORAGE_GROUP.equals(detail.getStorageGroup()));
        PreconditionUtils.checkArgument(determinable,
                "属性值-" + (operation == null ? "" : operation.getName())
                        + "要指定分组数据主键，不支持按对象下全部实例执行，请用单实体执行或行为测试：" + detail.getActionApi(),
                HttpStatus.BAD_REQUEST);
    }

    /**
     * 行为测试：用一条真实数据把行为定义跑一遍并落库。
     * <p>
     * 不加事务：属性值行为写数据湖、实例关系行为写图库、算子行为跨两者，
     * 且发现必填属性缺失时的失效标记必须独立提交，不能被随后的异常回滚掉。
     */
    @Override
    public ActionTestResultVO testAction(ActionTestParam param) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, param.getOntologySpaceId()));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        // 草稿可以试跑（发布前正是要拿它验证定义）
        PreconditionUtils.checkArgument(param.getEntityPrimaryKey() != null, "行为测试必须提供实体主键", HttpStatus.BAD_REQUEST);

        var detail = getActionByApi(param.getOntologySpaceId(), param.getActionApi());
        if (action.getActionKind() == ActionKindEnum.BASE) {
            if (action.getSubjectType() == ActionSubjectTypeEnum.PROPERTY_VALUE) {
                return testPropertyValueAction(action, detail, param);
            }
            if (action.getSubjectType() == ActionSubjectTypeEnum.LINK) {
                return testLinkAction(action, detail, param);
            }
        }
        return testFunctionAction(action, detail, param);
    }

    /**
     * 执行不绑算子的基础行为：属性值行为对分组表增删改一行，实例关系行为改图库一条边的状态与时段。
     * 行为测试与实例执行共用这套执行体，差别只在外部输入取值是否由调用方给。
     */
    @Override
    public String executeBaseAction(EntityActionExecuteParam param, ActionContextInfoDTO infoDTO) {
        var detail = infoDTO.getActionDetailVO();
        var action = getById(detail.getId());
        PreconditionUtils.checkNotNull(action, "行为不存在：" + detail.getActionApi(), HttpStatus.BAD_REQUEST);
        return runBaseAction(action, detail, param, infoDTO.getExternalParams());
    }

    /** 按基础行为类型分发执行体 */
    private String runBaseAction(OntologyAction action, OntologyActionDetailVO detail,
                                 EntityActionExecuteParam param, Map<String, Object> externalParams) {
        var subjectType = action.getSubjectType();
        if (subjectType == ActionSubjectTypeEnum.PROPERTY_VALUE) {
            return runPropertyValueAction(action, detail.getMappingIns(), param, externalParams);
        }
        if (subjectType == ActionSubjectTypeEnum.LINK) {
            return runLinkAction(action, detail.getLinkMapping(), param);
        }
        throw new BusinessException("该行为需要绑定算子后才能执行：" + action.getApi(), HttpStatus.BAD_REQUEST);
    }

    /** 行为测试的入参形状与执行不同，这里把测试给的键包成执行用的单实体参数 */
    private EntityActionExecuteParam baseExecuteParam(OntologyAction action, Object entityPrimaryKey,
                                                      Object dataPrimaryKey, Object linkedEntityPrimaryKey) {
        return EntityActionExecuteParam.builder()
                .actionApi(action.getApi())
                .ontologySpaceId(action.getOntologySpaceId())
                .ontologyUniqueIdentifier(action.getOntologyUniqueIdentifier())
                .entityPrimaryKey(entityPrimaryKey)
                .dataPrimaryKey(dataPrimaryKey)
                .linkedEntityPrimaryKey(linkedEntityPrimaryKey)
                .build();
    }

    /** 属性值行为测试：对目标存储分组对应的物理表真实增删改一行 */
    private ActionTestResultVO testPropertyValueAction(OntologyAction action, OntologyActionDetailVO detail, ActionTestParam param) {
        var executeParam = baseExecuteParam(action, param.getEntityPrimaryKey(), param.getDataPrimaryKey(), null);
        return testResult(action, runPropertyValueAction(action, detail.getMappingIns(), executeParam, param.getParams()));
    }

    /** 属性值行为执行体：对 action 的存储分组按基础操作增删改一行 */
    private String runPropertyValueAction(OntologyAction action, List<ActionParamMappingVO> mappings,
                                          EntityActionExecuteParam param, Map<String, Object> externalParams) {
        var ontologyIdentifier = action.getOntologyUniqueIdentifier();
        var storageGroup = action.getStorageGroup();
        var operation = action.getActionOperation();
        var entityPrimaryKey = param.getEntityPrimaryKey();

        var groupProps = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>()
                        .eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier))
                .stream()
                .filter(v -> storageGroup.equals(v.getStorageGroup()) && StringUtils.isNotEmpty(v.getDatasourceId()))
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(groupProps), "存储分组" + storageGroup + "未绑定数据源", HttpStatus.BAD_REQUEST);
        var propMap = groupProps.stream().collect(Collectors.toMap(OntologyProperty::getUniqueIdentifier, v -> v));

        if (operation == ActionOperationEnum.DELETE) {
            PreconditionUtils.checkNotNull(param.getDataPrimaryKey(), "属性值-删除必须提供分组数据主键", HttpStatus.BAD_REQUEST);
            entityService.deleteEntityProperty(EntityPropertyDeleteParam.builder()
                    .ontologyIdentifier(ontologyIdentifier)
                    .entityPrimaryKey(entityPrimaryKey)
                    .storageGroup(storageGroup)
                    .dataPrimaryKey(param.getDataPrimaryKey())
                    .build());
            return "已删除存储分组[" + storageGroup + "]中数据主键为 " + param.getDataPrimaryKey() + " 的属性行";
        }

        var values = resolvePropertyValues(mappings, ontologyIdentifier, entityPrimaryKey, externalParams);
        // 属性后来被挪出该分组时行为已跑不通，给明确提示而不是空指针
        var movedOut = values.keySet().stream().filter(id -> !propMap.containsKey(id))
                .collect(Collectors.joining("、"));
        PreconditionUtils.checkArgument(StringUtils.isEmpty(movedOut),
                "行为绑定的属性已不在存储分组[" + storageGroup + "]中，请重新编辑发布：" + movedOut, HttpStatus.BAD_REQUEST);
        List<EntityCreateParam.PropertyInfo> props = values.entrySet().stream()
                .map(entry -> EntityCreateParam.PropertyInfo.builder()
                        .propertyApiName(propMap.get(entry.getKey()).getApiName())
                        .propertyValue(entry.getValue())
                        .build())
                .collect(Collectors.toList());
        PreconditionUtils.checkArgument(!props.isEmpty(), "该行为没有绑定任何属性，无法执行", HttpStatus.BAD_REQUEST);

        if (operation == ActionOperationEnum.CREATE) {
            checkUnboundNotNullColumns(action, groupProps, values.keySet());
            entityService.insertEntityProperty(EntityPropertyInsertParam.builder()
                    .ontologyIdentifier(ontologyIdentifier)
                    .entityPrimaryKey(entityPrimaryKey)
                    .entityProperties(Collections.singletonList(EntityCreateParam.EntityProperty.builder()
                            .storageGroup(storageGroup)
                            .props(Collections.singletonList(props))
                            .build()))
                    .build());
            return "已向存储分组[" + storageGroup + "]新增 1 行属性数据，实体主键 " + entityPrimaryKey;
        }

        // main 分组的行主键就是实体主键，批量执行不传数据主键时按实体主键定位
        var dataPrimaryKey = param.getDataPrimaryKey() == null && MAIN_STORAGE_GROUP.equals(storageGroup)
                ? entityPrimaryKey : param.getDataPrimaryKey();
        PreconditionUtils.checkNotNull(dataPrimaryKey, "属性值-修改必须提供分组数据主键", HttpStatus.BAD_REQUEST);
        entityService.updateEntityProperty(EntityPropertyUpdateParam.builder()
                .ontologyIdentifier(ontologyIdentifier)
                .entityPrimaryKey(entityPrimaryKey)
                .storageGroup(storageGroup)
                .dataPrimaryKey(dataPrimaryKey)
                .props(props)
                .build());
        return "已更新存储分组[" + storageGroup + "]中数据主键 " + dataPrimaryKey + " 的 " + props.size() + " 个属性值";
    }

    /**
     * 行为定义之后分组里新增的必填属性，行为测试时会被写成 null；
     * 一旦命中非空约束，这个行为往后也跑不通，直接置为失效并提示重新编辑发布。
     *
     * @param boundPropertyIds 行为定义里已绑定取值的属性
     */
    private void checkUnboundNotNullColumns(OntologyAction action, List<OntologyProperty> groupProps, Set<String> boundPropertyIds) {
        var notNullColumns = tableMetadataMapper.queryNotNullColumns(groupProps.get(0).getDatasourceSchema(), groupProps.get(0).getDatasourceId());
        if (CollectionUtils.isEmpty(notNullColumns)) {
            return;
        }
        var blocking = groupProps.stream()
                .filter(v -> v.getIsPrimaryKey() == 0)
                .filter(v -> !boundPropertyIds.contains(v.getUniqueIdentifier()))
                .filter(v -> notNullColumns.contains(v.getDatasourceColumnName()))
                .map(OntologyProperty::getDisplayName)
                .collect(Collectors.joining("、"));
        if (StringUtils.isEmpty(blocking)) {
            return;
        }
        throw new BusinessException("存储分组[" + action.getStorageGroup() + "]的必填属性未绑定取值："
                + blocking, HttpStatus.BAD_REQUEST);
    }

    /** 按参数绑定的来源取属性值：对象属性读实例当前值，固定值解析字面量，外部输入取调用方传入的参数 */
    private Map<String, Object> resolvePropertyValues(List<ActionParamMappingVO> mappings, String ontologyIdentifier,
                                                      Object entityPrimaryKey, Map<String, Object> externalParams) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(mappings)) {
            return values;
        }
        Map<String, List<Object>> entityDetail = null;
        for (var mapping : mappings) {
            var sourceType = mapping.getSourceType() == null ? ActionParamSourceEnum.EXTERNAL : mapping.getSourceType();
            Object value;
            if (sourceType == ActionParamSourceEnum.PROPERTY) {
                if (entityDetail == null) {
                    entityDetail = entityService.getEntityDetail(ontologyIdentifier, entityPrimaryKey).stream()
                            .collect(Collectors.toMap(EntityPropertyDetailVO::getPropertyUniqIdentifier, EntityPropertyDetailVO::getPropertyValues));
                }
                var currentValues = entityDetail.get(mapping.getPropertyUniqueIdentifier());
                value = CollectionUtils.isEmpty(currentValues) ? null : currentValues.get(0);
            } else if (sourceType == ActionParamSourceEnum.FIXED) {
                value = parseFixedValue(mapping.getSourceConfig());
            } else if (sourceType == ActionParamSourceEnum.EXTERNAL) {
                value = externalParams == null ? null : externalParams.get(mapping.getParameterName());
            } else {
                throw new BusinessException("参数[" + mapping.getParameterName() + "]的输入来源暂不支持执行："
                        + sourceType.getName(), HttpStatus.BAD_REQUEST);
            }
            values.put(mapping.getPropertyUniqueIdentifier(), value);
        }
        return values;
    }

    /** 固定值按 JSON 字面量解析，不是合法 JSON 时按原文本返回 */
    private Object parseFixedValue(String sourceConfig) {
        if (StringUtils.isEmpty(sourceConfig)) {
            return null;
        }
        try {
            return jsonMapper.readValue(sourceConfig, Object.class);
        } catch (Exception e) {
            return sourceConfig;
        }
    }

    /** 实例关系行为测试：真实写入/改状态/失效图库的一条边 */
    private ActionTestResultVO testLinkAction(OntologyAction action, OntologyActionDetailVO detail, ActionTestParam param) {
        var executeParam = baseExecuteParam(action, param.getEntityPrimaryKey(), null, param.getLinkEntityPrimaryKey());
        return testResult(action, runLinkAction(action, detail.getLinkMapping(), executeParam));
    }

    /** 实例关系行为执行体：按定义的目标状态与持续时段改图库里的那条边 */
    private String runLinkAction(OntologyAction action, ActionLinkMappingParam linkMapping, EntityActionExecuteParam param) {
        PreconditionUtils.checkArgument(linkMapping != null, "行为没有绑定关系，无法执行", HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(param.getLinkedEntityPrimaryKey() != null, "实例关系行为必须提供对端实体主键", HttpStatus.BAD_REQUEST);
        var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getUniqueIdentifier, linkMapping.getOntologyLinkUniqIdentifier()));
        PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + linkMapping.getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);

        // 行为挂在对象的一侧，执行给的实体主键就落在关系的那一端
        var selfIsFrom = action.getOntologyUniqueIdentifier().equals(linkGroup.getOntologyUniqueIdentifierFrom());
        var primaryKeyFrom = selfIsFrom ? param.getEntityPrimaryKey() : param.getLinkedEntityPrimaryKey();
        var primaryKeyTo = selfIsFrom ? param.getLinkedEntityPrimaryKey() : param.getEntityPrimaryKey();
        var linkId = linkMapping.getOntologyLinkUniqIdentifier();
        var operation = action.getActionOperation();

        if (operation == ActionOperationEnum.DELETE) {
            entityService.deleteEntityRelation(linkId, primaryKeyFrom, primaryKeyTo);
            return "已将实例关系[" + linkId + "]置为删除状态";
        }
        // 新增按定义写状态与时段；修改只改定义里填了的项，没填的保留边上原值
        var definedStatus = linkMapping.getTargetStatus();
        // 改造前建的新增行为状态被强制留空，这里按当时的固定值 ENABLE 处理，不让老行为执行结果发生变化
        var status = operation == ActionOperationEnum.UPDATE ? definedStatus
                : (definedStatus == null ? Status.ENABLE : definedStatus);
        entityService.updateEntityRelation(EntityRelationUpdateParam.builder()
                .linkUniqIdentifier(linkId)
                .entityPrimaryKeyFrom(primaryKeyFrom)
                .entityPrimaryKeyTo(primaryKeyTo)
                .visibilityWindows(linkMapping.getVisibilityWindows())
                .status(status)
                .partialUpdate(operation == ActionOperationEnum.UPDATE)
                .build());
        return operation == ActionOperationEnum.UPDATE
                ? "已将实例关系[" + linkId + "]状态改为 " + status.getName()
                : "已写入实例关系[" + linkId + "]（状态 " + status.getName() + "）";
    }

    /** 算子类行为测试：走既有执行链路，算子输出按输出方式真实回写 */
    @SneakyThrows
    private ActionTestResultVO testFunctionAction(OntologyAction action, OntologyActionDetailVO detail, ActionTestParam param) {
        var executeParamBuilder = EntityActionExecuteParam.builder()
                .actionApi(action.getApi())
                .ontologySpaceId(action.getOntologySpaceId())
                .ontologyUniqueIdentifier(action.getOntologyUniqueIdentifier())
                .entityPrimaryKey(param.getEntityPrimaryKey());
        // 指定了对端实例时只跑这一对，否则会遍历关系下的全部实体
        if (detail.getLinkMapping() != null && param.getLinkEntityPrimaryKey() != null) {
            var linkGroup = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>()
                    .eq(OntologyLinkGroup::getUniqueIdentifier, detail.getLinkMapping().getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(linkGroup != null, "无效的对象关系：" + detail.getLinkMapping().getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
            // 对端是关系的另一端，不是固定的 To：行为可能挂在关系的 To 侧
            executeParamBuilder.linkedOntologyUniqueIdentifier(entityService.counterpartOntology(linkGroup, action.getOntologyUniqueIdentifier()))
                    .linkedEntityPrimaryKey(param.getLinkEntityPrimaryKey());
        }
        var executeParam = executeParamBuilder.build();

        var contextInfoDTO = entityService.initActionContextInfoDTO(executeParam);
        contextInfoDTO.setExternalParams(param.getParams());
        var result = entityService.executeEntityAction(executeParam, contextInfoDTO, entityService.getLinkedEntities(contextInfoDTO));
        return testResult(action, "已按行为定义执行算子并完成落库").setResult(result);
    }

    private ActionTestResultVO testResult(OntologyAction action, String message) {
        return ActionTestResultVO.builder()
                .actionApi(action.getApi())
                .message(message)
                .build();
    }

    @Override
    @Transactional
    public Long createScheduling(ActionSchedulingCreateParam param) {
        //todo 目前只考虑定时调度
        var schedulingType = param.getType();
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
        PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, meta.getOntologySpaceId()));
        PreconditionUtils.checkNotNull(action, "action不存在：" + param.getActionApi());
        // 属性值、实例关系行为没有算子，执行时会对对象下全部实例逐个落库，定时跑会造成重复数据，不支持调度
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(action.getFunctionApi()),
                "不绑定算子的行为不支持配置调度：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        Long id = null;
        //允许同一个行为配置多个调度策略
        if (schedulingType.equals(ActionSchedulingTypeEnum.TASK)) {
            var taskParam = param.getTask();
            var actionHandleTask = ActionHandleTask.builder()
                    .actionId(action.getId())
                    .cron(taskParam.getTaskCronExpression())
                    .name(param.getName())
                    .description(param.getDescription())
                    .status(ScheduleStatus.STOP)
                    .build();
            actionHandleTaskService.save(actionHandleTask);
            id = actionHandleTask.getId();
            try {
                var jonInfoId = xxlJobClient.createJobInfo(param.getName(),
                        taskParam.getTaskCronExpression(),
                        XxlJobActionExecuteParam.builder()
                                .actionApi(param.getActionApi())
                                .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                                .scheduleId(id)
                                .build());
                actionHandleTaskService.updateById(actionHandleTask.setRemark(jonInfoId));
            } catch (Exception e) {
                log.error("createScheduling failed", e);
                throw new BusinessException("远程调用xxl-job创建jobinfo失败");
            }
        }
        return id;
    }

    @Override
    @Transactional
    public void updateScheduling(ActionSchedulingUpdateParam param) {
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, param.getOntologyIdentifier()));
        PreconditionUtils.checkNotNull(meta, "无效的对象：" + param.getOntologyIdentifier(), HttpStatus.BAD_REQUEST);
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getApi, param.getActionApi())
                .eq(OntologyAction::getOntologySpaceId, meta.getOntologySpaceId()));
        PreconditionUtils.checkNotNull(action, "action不存在：" + param.getActionApi());
        //todo 目前只考虑定时调度
        var schedulingType = param.getType();
        if (schedulingType.equals(ActionSchedulingTypeEnum.TASK)) {
            var handleTask = actionHandleTaskService.getById(param.getId());
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + param.getId());
            handleTask.setActionId(action.getId())
                    .setDescription(param.getDescription())
                    .setCron(param.getTask().getTaskCronExpression())
                    .setName(param.getName());
            actionHandleTaskService.updateById(handleTask);
            try {
                xxlJobClient.updateJobInfo(
                        handleTask.getRemark(),
                        param.getName(),
                        param.getTask().getTaskCronExpression(),
                        XxlJobActionExecuteParam.builder()
                                .scheduleId(param.getId())
                                .actionApi(param.getActionApi())
                                .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                                .build());
            } catch (Exception e) {
                log.error("updateJobInfo failed", e);
                throw new BusinessException("远程调用xxl-job更新jobinfo失败");
            }
        }
    }

    @Override
    @Transactional
    public void removeScheduling(ActionSchedulingTypeEnum type, Long id) {
        //todo 只考虑定时任务
        if (ActionSchedulingTypeEnum.TASK.equals(type)) {
            var handleTask = actionHandleTaskService.getById(id);
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + id);
            actionHandleTaskService.removeById(handleTask.getId());
            try {
                xxlJobClient.removeJob(handleTask.getRemark());
            } catch (Exception e) {
                log.error("removeScheduling failed", e);
                throw new BusinessException("远程调用xxl-job删除任务失败");
            }
        }
    }

    @Override
    @Transactional
    public void startScheduling(ActionSchedulingTypeEnum type, Long id) {
        //todo 只考虑定时任务
        if (ActionSchedulingTypeEnum.TASK.equals(type)) {
            var handleTask = actionHandleTaskService.getById(id);
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + id);
            actionHandleTaskService.updateById(handleTask.setStatus(ScheduleStatus.START));
            try {
                xxlJobClient.startJob(handleTask.getRemark());
            } catch (Exception e) {
                log.error("startScheduling failed", e);
                throw new BusinessException("远程调用xxl-job启动任务失败");
            }
        }
    }

    @Override
    @Transactional
    public void stopScheduling(ActionSchedulingTypeEnum type, Long id) {
        //todo 只考虑定时任务
        if (ActionSchedulingTypeEnum.TASK.equals(type)) {
            var handleTask = actionHandleTaskService.getById(id);
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + id);
            actionHandleTaskService.updateById(handleTask.setStatus(ScheduleStatus.STOP));
            try {
                xxlJobClient.stopJob(handleTask.getRemark());
            } catch (Exception e) {
                log.error("stopScheduling failed", e);
                throw new BusinessException("远程调用xxl-job暂停任务失败");
            }
        }

    }

    @Override
    public ActionSchedulingDetailVO getSchedulingDetailById(Long id, ActionSchedulingTypeEnum type) {
        //todo 只考虑定时任务
        if (ActionSchedulingTypeEnum.TASK.equals(type)) {
            var handleTask = actionHandleTaskService.getById(id);
            PreconditionUtils.checkNotNull(handleTask, "定时调度任务不存在：" + id);
            var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getId, handleTask.getActionId()));
            var ontology = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, action.getOntologyUniqueIdentifier()));
            return ActionSchedulingDetailVO.builder()
                    .actionApi(action.getApi())
                    .actionName(action.getDisplayName())
                    .ontologyName(ontology.getDisplayName())
                    .schedulingName(handleTask.getName())
                    .id(handleTask.getId())
                    .description(handleTask.getDescription())
                    .type(ActionSchedulingTypeEnum.TASK)
                    .ontologyIdentifier(action.getOntologyUniqueIdentifier())
                    .taskVO(ActionHandleTaskInfoVO.builder().taskCronExpression(handleTask.getCron()).build())
                    .status(handleTask.getStatus())
                    .build();
        }
        return null;
    }

    @Override
    public Page<ActionSchedulingInfoVO> listScheduling(Integer pageNum, Integer pageSize) {
        //todo 只考虑定时任务
        var pageInfo = new Page<ActionHandleTask>(pageNum, pageSize).addOrder(OrderItem.desc("id"));
        var pages = actionHandleTaskService.page(pageInfo);
        var records = pages.getRecords();

        if (CollectionUtils.isEmpty(records)) {
            return new Page<ActionSchedulingInfoVO>(pageNum, pageSize);
        }

        var actionIds = records.stream().map(v -> v.getActionId()).collect(Collectors.toList());
        var actionMap = actionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, actionIds))
                .stream().collect(Collectors.toMap(v -> v.getId(), v -> v, (existingValue, newValue) -> existingValue));

        var ontologyUniqIds = actionMap.values().stream().map(v -> v.getOntologyUniqueIdentifier()).collect(Collectors.toList());
        var ontologyMap = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, ontologyUniqIds))
                .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v, (existingValue, newValue) -> existingValue));


        var vo = records.stream().<ActionSchedulingInfoVO>map(v -> {
            var actionId = v.getActionId();
            var action = actionMap.get(actionId);
            var ontologyMeta = ontologyMap.get(action.getOntologyUniqueIdentifier());
            return ActionSchedulingInfoVO.builder()
                    .actionApi(action.getApi())
                    .actionName(action.getDisplayName())
                    .ontologyName(ontologyMeta.getDisplayName())
                    .schedulingName(v.getName())
                    .id(v.getId())
                    .description(v.getDescription())
                    .type(ActionSchedulingTypeEnum.TASK)
                    .ontologyIdentifier(ontologyMeta.getUniqueIdentifier())
                    .status(v.getStatus())
                    .build();

        }).collect(Collectors.toList());


        return new Page<ActionSchedulingInfoVO>(pageNum, pageSize)
                .setTotal(pages.getTotal())
                .setRecords(vo);

    }

    @Override
    public Page<SchedulingResultVO> getSchedulingResult(Long id, ActionSchedulingTypeEnum type, Integer pageNum, Integer pageSize) {
        //todo 只考虑定时任务
        var pageInfo = new Page<ActionHandleLog>(pageNum, pageSize).addOrder(OrderItem.desc("id"));
        var pages = actionHandleLogMapper.selectPage(pageInfo, new LambdaQueryWrapper<ActionHandleLog>()
                .eq(ActionHandleLog::getActionHandleId, id)
                .eq(ActionHandleLog::getActionHandleType, ActionSchedulingTypeEnum.TASK));
        var records = pages.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            return new Page<>(pageNum, pageSize);
        }
        var vo = records.stream().map(v -> SchedulingResultVO.builder()
                        .msg(v.getMsg())
                        .taskStatus(v.getTaskStatus())
                        .requestParam(v.getRequestParam())
                        .triggerTime(v.getTriggerTime())
                        .completeTime(v.getCompleteTime())
                        .build())
                .collect(Collectors.toList());

        return new Page<SchedulingResultVO>(pageNum, pageSize)
                .setTotal(pages.getTotal())
                .setRecords(vo);
    }


}
