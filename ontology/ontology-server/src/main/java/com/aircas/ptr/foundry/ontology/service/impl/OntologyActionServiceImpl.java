package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.*;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.exception.*;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyActionBo;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyActionMappingInBO;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionHandleMappingInParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionHandleRuleAddParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionLinkMappingParam;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OntologyActionServiceImpl extends ServiceImpl<OntologyActionMapper, OntologyAction> implements OntologyActionService {

    @Resource
    private OntologyLinkGroupMapper linkGroupMapper;

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private FunctionParamMapper functionParamMapper;

    @Resource
    private OntologyActionLinkMapper actionLinkMapper;

    @Resource
    private OntologyActionMapper ontologyActionMapper;

    @Resource
    private OntologyActionMappingInMapper ontologyActionMappingInMapper;

    @Resource
    private OntologyActionMappingInService ontologyActionMappingInService;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;


    @Resource
    private ActionHandleCommitFlashMermoryMapper actionHandleCommitFlashMermoryMapper;

    @Resource
    private OntologyPropertyMapper propertyMapper;

    @Resource
    private ObjectService objectService;

    @Resource
    private OntologyPropertyService ontologyPropertyService;

    @Resource
    private FunctionService functionService;

    @Resource
    private ActionHandleRuleService actionHandleRuleService;

    @Resource
    private ActionHandleTaskService actionHandleTaskService;

    private final static ObjectMapper objectMapper = new ObjectMapper();

    private final static String DEFAULT_OBJECT_DESC = "当前本体对象";


    @Override
    public void removeByOntologyIdentifier(String ontologyIdentifier) {
        var actions = list(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, ontologyIdentifier));
        if (CollectionUtils.isNotEmpty(actions)) {
            var actionIds = actions.stream().map(v -> v.getId()).collect(Collectors.toList());
            actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().in(OntologyActionLink::getOntologyActionId, actionIds));
            ontologyActionMapper.delete(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, actionIds));
            ontologyActionMappingInMapper.delete(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, actionIds));
            actionHandleRuleService.remove(new LambdaQueryWrapper<ActionHandleRule>().in(ActionHandleRule::getActionId, actionIds));
            actionHandleTaskService.remove(new LambdaQueryWrapper<ActionHandleTask>().in(ActionHandleTask::getActionId, actionIds));
        }
    }

    @Override
    public Object handle(String primaryKey, String api, List<ActionHandleMappingInParam> params)
            throws FunctionClassNotNewInstanceException,
            FunctionFileNotCompiled,
            FunctionRuntimeException,
            FunctionNotFoundException,
            OntologyFunctionNotFoundException,
            OntologyApiNameNotFoundException,
            OntologyFunctionMappedPropertyNotFoundException {

        return null;

//        HashMap<String, Object> parameters = new HashMap<>();
//        OntologyActionVO actionVO = getMetadataByApi(api);
//        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(actionVO.getOntologyUniqueIdentifier());
//        if (ontologyMeta == null) {
//            throw ExceptionFactory.getOntologyApiNameNotFoundException(null);
//        }
//
//        //根据mapping结果，把property注入到parameters
//        ObjectOneInfoVO objectOneInfoVO = objectService.queryObjectByPrimaryKey(ontologyMeta.getUniqueIdentifier(), primaryKey);
//        List<PropertyValueVO> propertyList = objectOneInfoVO.getProperties();
//        Map<String, PropertyValueVO> propertyMap = new HashMap<>();
//        propertyList.forEach(propertyValueVO -> propertyMap.put(propertyValueVO.getUniqueIdentifier(), propertyValueVO));
//
//        List<OntologyActionMappingInVO> mappingIns = actionVO.getMappingIns();
//        for (OntologyActionMappingInVO mappingIn : mappingIns) {
//            String parameterName = mappingIn.getParameterName();
//            String propertyUniqueIdentifier = mappingIn.getPropertyUniqueIdentifier();
//            if (ONTOLOGY.getCode().equals(propertyUniqueIdentifier)) {
//                // TODO:如果参数是实体本身，需要将实体作为参数传入，现在逻辑还不完善
//                Map<String, Object> objectMap = new HashMap<>();
//                objectMap.put("primaryKey", primaryKey);
//                objectMap.put("api", api);
//                parameters.put(parameterName, objectMap);
//            } else {
//                PropertyValueVO propertyValueVO = propertyMap.get(propertyUniqueIdentifier);
//                Object value = propertyValueVO.getValue();
//                parameters.put(parameterName, value);
//            }
//        }
//        // 如果参数不为空，将所有的输入参数加入函数的parameters
//        // TODO: 参数类型不对应时，需要转换？
//        if (params != null && !params.isEmpty()) {
//            params.forEach(param -> {
//                parameters.put(param.getParameterName(), param.getParameterValue());
//            });
//        }
//        //根据property的值，设置参数的值即可，如果是当前对象，则设置为当前对象，也就是currentObject即可，包含api 和primaryKey
//        Object result = functionService.handle(actionVO.getFunctionApi(), false, null, parameters);
//        // TODO: 临时添加，模拟行为具体执行日志。
//        log.info(result.toString());
//        // TODO: 保存行为执行结果。
//
//        return result;
//    }
//
//    @Override
//    public int save(OntologyActionBo ontologyActionBo) {
//
//        // TODO:暂时不校验
////        checkBindingConsistence(ontologyActionBo);
//        OntologyAction ontologyAction = new OntologyAction();
//        BeanUtils.copyProperties(ontologyActionBo, ontologyAction);
//        Date now = new Date();
//        ontologyAction.setCreateTime(now);
//        ontologyAction.setUpdateTime(now);
//        ontologyAction.setId(SnowflakeIdUtil.get());
//        int status = ontologyActionMapper.insert(ontologyAction);
//        if (ontologyActionBo.getMappingIns() == null || ontologyActionBo.getMappingIns().isEmpty()) {
//            return status;
//        }
//        long id = ontologyAction.getId();
//        for (OntologyActionMappingInBO mappingInBO : ontologyActionBo.getMappingIns()) {
//            OntologyActionMappingIn mappingIn = new OntologyActionMappingIn();
//            BeanUtils.copyProperties(mappingInBO, mappingIn);
//            mappingIn.setOntologyActionId(id);
//            mappingIn.setCreateTime(now);
//            mappingIn.setUpdateTime(now);
//            mappingIn.setId(SnowflakeIdUtil.get());
//            status = ontologyActionMappingInMapper.insert(mappingIn);
//        }
//        return status;
    }


    @Override
    public int update(OntologyActionBo ontologyActionBo) {

        OntologyAction ontologyAction = new OntologyAction();
        BeanUtils.copyProperties(ontologyActionBo, ontologyAction);
        int status = ontologyActionMapper.updateById(ontologyAction);
        if (ontologyActionBo.getMappingIns() == null || ontologyActionBo.getMappingIns().size() == 0) {
            return status;
        }
        for (OntologyActionMappingInBO mappingInBO : ontologyActionBo.getMappingIns()) {
            OntologyActionMappingIn mappingIn = new OntologyActionMappingIn();
            BeanUtils.copyProperties(mappingInBO, mappingIn);
            status = ontologyActionMappingInMapper.updateById(mappingIn);
        }
        return status;
    }

    @Override
    public void handleTask(Long actionHandleTaskId) {

//        ActionHandleTaskBO actionHandleTaskBO = actionHandleTaskService.selectById(actionHandleTaskId);
//        String objectPrimaryKeys = actionHandleTaskBO.getObjectPrimaryKey();
//        OntologyAction action = ontologyActionMapper.selectById(actionHandleTaskBO.getActionId());
//        if (StringUtils.isBlank(objectPrimaryKeys)) {
//            return;
//        }
//        Arrays.stream(objectPrimaryKeys.split(",")).forEach(objectKey -> {
//            try {
//                handle(objectKey, action.getApi(), null);
//            } catch (FunctionClassNotNewInstanceException | FunctionFileNotCompiled | FunctionRuntimeException |
//                    FunctionNotFoundException | OntologyFunctionNotFoundException | OntologyApiNameNotFoundException |
//                    OntologyFunctionMappedPropertyNotFoundException e) {
//                throw new RuntimeException(e);
//            }
//        });
    }

    @Override
    public int getCountByStatus(int status) {
        return ontologyActionMapper.selectCount(new QueryWrapper<OntologyAction>().eq("status", status));
    }

    @Override
    public boolean configRule(String actionApi, List<String> objectPrimaryKeys, List<ActionHandleRuleAddParam> rules, ActionRuleConnectType ruleConnectType) {

        return true;
//        // 0.更新action的handle类型
//        OntologyAction action = ontologyActionMapper.selectByApi(actionApi);
//        action.setHandleType(RULE.getCode());
//        ontologyActionMapper.updateById(action);
//        // 1.插入数据库
//        ActionHandleRuleBO actionHandleRuleBO = new ActionHandleRuleBO();
//        actionHandleRuleBO.setActionId(action.getId());
//        // TODO: 需要考虑所有实体情况
//        actionHandleRuleBO.setObjectPrimaryKey(String.join(",", objectPrimaryKeys));
//        actionHandleRuleBO.setRuleConnectType(ruleConnectType.getCode());
//        actionHandleRuleBO.setRules(JSON.toJSONString(rules));
//        actionHandleRuleService.insert(actionHandleRuleBO);
//        // TODO: 2.调用数据更改任务，插入任务
//        return true;
    }

    @Override
    public List<ActionHandleRule> getActionRulesById(List<Long> ids) {
        return actionHandleRuleService.queryRulesByIds(ids);
    }

    public int updateActionRulesById(ActionHandleRule rule) {
        return actionHandleRuleService.updateRules(rule);
    }


    public ActionHandleCommitFlashMemory getActionDataLogByAction(long actionId, String primaryValue) {
        return actionHandleCommitFlashMermoryMapper.getActionFlashMemoryByActionAndPrimary(actionId, primaryValue);
    }

    public int updateActionDataById(ActionHandleCommitFlashMemory flashMemory) {
        return actionHandleCommitFlashMermoryMapper.updateActionDataLogById(flashMemory);
    }

    @Override
    public boolean configTask(String actionApi, List<String> objectPrimaryKeys, Date taskStartTime, Date taskEndTime, String taskCorn) {

        return true;
//        // 0.更新action的handle类型
//        OntologyAction action = ontologyActionMapper.selectByApi(actionApi);
//        action.setHandleType(TASK.getCode());
//        ontologyActionMapper.updateById(action);
//
//        ActionHandleTaskBO actionHandleTaskBO = new ActionHandleTaskBO();
//        actionHandleTaskBO.setActionId(action.getId());
//        actionHandleTaskBO.setObjectPrimaryKey(String.join(",", objectPrimaryKeys));
//        actionHandleTaskBO.setStartTime(taskStartTime);
//        actionHandleTaskBO.setEndTime(taskEndTime);
//        actionHandleTaskBO.setCorn(taskCorn);
//
//        return actionHandleTaskService.insert(actionHandleTaskBO) > 0;
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void createAction(ActionCreateOrUpdateParam param) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, param.getActionApi()));
        PreconditionUtils.checkArgument(action == null, "action api 已存在：" + param.getActionApi(), HttpStatus.BAD_REQUEST);
        var ontologyAction = OntologyAction.builder()
                .api(param.getActionApi())
                .description(param.getDescription())
                .displayName(param.getDisplayName())
                .functionApi(param.getFunctionApi())
                .icon(param.getIcon())
                .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                .status(Status.ENABLE.getValue())
                .build();
        save(ontologyAction);

        var funcApi = param.getFunctionApi();
        if (StringUtils.isEmpty(funcApi)) {
            return;
        }
        var func = functionMapper.selectOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, funcApi));
        //目前仅支行为与自定义函数api绑定
        PreconditionUtils.checkArgument(func != null && func.getType().equals(FunctionTypeEnum.CUSTOMIZE), "function api 不存在：" + func, HttpStatus.BAD_REQUEST);
        var functionParams = functionParamMapper.selectList(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, func.getId()));
        //校验本体关系
        var ontologyId = param.getOntologyIdentifier();
        var link = param.getLinkMapping();
        if (link != null) {
            var ontologyLink = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getId, link.getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(ontologyLink != null && ontologyLink.getOntologyUniqueIdentifierFrom().equals(ontologyId), "无效的本体关系" + link.getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);

            var output = functionParams.stream().filter(p -> p.getCategory().equals(FunctionParamCategoryEnum.OUTPUT)).findFirst().orElse(null);
            PreconditionUtils.checkArgument(output != null, "函数无输出参数,functionId:" + func.getId());
            actionLinkMapper.insert(OntologyActionLink.builder()
                    .ontologyActionId(ontologyAction.getId())
                    .ontologyLinkParamExpression(link.getOntologyLinkFunctionParamExpression())
                    .ontologyLinkUniqueIdentifier(link.getOntologyLinkUniqIdentifier())
                    .build());
        }

        //校验函数输入参数
        var inputMap = functionParams.stream().filter(p -> p.getCategory().equals(FunctionParamCategoryEnum.INPUT))
                .collect(Collectors.toMap(v -> v.getId(), v -> v));

        if (MapUtils.isNotEmpty(inputMap)) {
            var mappingIns = param.getMappingIns();
            PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(mappingIns), "缺少行为输入参数", HttpStatus.BAD_REQUEST);
            List<OntologyActionMappingIn> list = Lists.newArrayList();
            mappingIns.forEach(mapping -> {
                var propertyId = mapping.getPropertyUniqueIdentifier();
                //本体属性是否存在，函数参数是否存在
                var prop = propertyMapper.selectOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getUniqueIdentifier, propertyId));
                PreconditionUtils.checkArgument(prop != null && inputMap.keySet().contains(mapping.getFunctionParamId()), "无效的行为参数：" + propertyId, HttpStatus.BAD_REQUEST);
                //行为属性类型与函数参数类型是否匹配
                var dataType = OntologyDataTypeEnum.valueOfDataType(inputMap.get(mapping.getFunctionParamId()).getParamType());
                PreconditionUtils.checkArgument(dataType.equals(OntologyDataTypeEnum.Array) || prop.getPropertyType().equals(dataType), "行为属性类型与函数参数类型不匹配：", HttpStatus.BAD_REQUEST);
                list.add(OntologyActionMappingIn.builder()
                        .ontologyActionId(ontologyAction.getId())
                        .functionParamExpression(mapping.getFunctionParamExpression())
                        .functionParamId(mapping.getFunctionParamId())
                        .propertyUniqueIdentifier(mapping.getPropertyUniqueIdentifier())
                        .build());
            });
            ontologyActionMappingInService.saveBatch(list);
        }
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateAction(ActionCreateOrUpdateParam param) {
        deleteActionByApi(param.getActionApi());
        createAction(param);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteActionByApi(String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, actionApi));
        PreconditionUtils.checkArgument(action != null, "行为不存在", HttpStatus.BAD_REQUEST);
        var actionHandleRule = actionHandleRuleService.getOne(new LambdaQueryWrapper<ActionHandleRule>()
                .eq(ActionHandleRule::getActionId, action.getId()).eq(ActionHandleRule::getStatus, Status.ENABLE.getValue()));
        var actionHandleTask = actionHandleTaskService.getOne(new LambdaQueryWrapper<ActionHandleTask>()
                .eq(ActionHandleTask::getActionId, action.getId()).eq(ActionHandleTask::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkArgument(actionHandleRule == null && actionHandleTask == null, "该行为被调度中，不能删除", HttpStatus.BAD_REQUEST);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        ontologyActionMapper.delete(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, action.getId()));
        ontologyActionMappingInMapper.delete(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, action.getId()));
    }

    @Override
    public Page<OntologyActionInfoVO> pageGetActionByOntologyId(String ontologyUniqIdentifier, Integer pageNum, Integer pageSize) {
        Page<OntologyActionInfoVO> result = new Page<>(pageNum, pageSize);
        var pageResult = page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, ontologyUniqIdentifier));
        List<OntologyActionInfoVO> records = pageResult.getRecords().stream()
                .map(v -> OntologyActionInfoVO.builder()
                        .actionApi(v.getApi())
                        .description(v.getDescription())
                        .ontologyUniqIdentifier(v.getOntologyUniqueIdentifier())
                        .displayName(v.getDisplayName())
                        .icon(v.getIcon())
                        .build())
                .collect(Collectors.toList());
        result.setRecords(records).setTotal(pageResult.getTotal());
        return result;
    }

    @Override
    public OntologyActionDetailVO getActionByApi(String actionApi) {
        var action = getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, actionApi));
        PreconditionUtils.checkArgument(action != null, "无效的action api", HttpStatus.BAD_REQUEST);
        var detailVO = OntologyActionDetailVO
                .builder()
                .functionApi(action.getFunctionApi())
                .actionApi(action.getApi())
                .description(action.getDescription())
                .displayName(action.getDisplayName())
                .ontologyUniqIdentifier(action.getOntologyUniqueIdentifier())
                .icon(action.getIcon())
                .build();
        var link = actionLinkMapper.selectOne(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        if (link != null) {
            detailVO.setLinkMapping(ActionLinkMappingParam.builder()
                    .ontologyLinkFunctionParamExpression(link.getOntologyLinkParamExpression())
                    .ontologyLinkUniqIdentifier(link.getOntologyLinkUniqueIdentifier())
                    .build());
        }
        var mappings = ontologyActionMappingInService.list(new LambdaQueryWrapper<OntologyActionMappingIn>().eq(OntologyActionMappingIn::getOntologyActionId, action.getId()));
        if (CollectionUtils.isNotEmpty(mappings)) {
            var mappingVOS = mappings.stream().map(v -> ActionParamMappingVO.builder()
                            .functionParamExpression(v.getFunctionParamExpression())
                            .functionParamId(v.getFunctionParamId())
                            .propertyUniqueIdentifier(v.getPropertyUniqueIdentifier())
                            .build())
                    .collect(Collectors.toList());
            detailVO.setMappingIns(mappingVOS);
        }
        return detailVO;
    }

//    private void checkBindingConsistence(OntologyActionBo ontologyFunctionBo)
//            throws OntologyFunctionParameterPropertyTypeNotSameException, FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException, OntologyFunctionBindingParameterNotFoundException, OntologyFunctionMappedPropertyNotFoundException {
//
//        String ontologUniqueIdentifier = ontologyFunctionBo.getOntologyUniqueIdentifier();
//        List<OntologyPropertyVO> propertyVOS = ontologyPropertyService.selectByOntologyUniqueIdentifier(ontologUniqueIdentifier);
//        Map<String, OntologyPropertyVO> propertyMap = new HashMap<>();
//        propertyVOS.forEach(propertyVO -> {
//            propertyMap.put(propertyVO.getUniqueIdentifier(), propertyVO);
//        });
//
//        List<ParameterMetadataVO> parameterMetadataVOS = functionService.getParameters(ontologyFunctionBo.getFunctionApi());
//        Map<String, ParameterMetadataVO> parameterMetadataMap = new HashMap<>();
//        parameterMetadataVOS.forEach(parameterMetadataVO -> {
//            parameterMetadataMap.put(parameterMetadataVO.getName(), parameterMetadataVO);
//        });
//
//        for (OntologyActionMappingInBO mappingInBO : ontologyFunctionBo.getMappingIns()) {
//            String parameterName = mappingInBO.getParameterName();
//            String propertyUniqueIdentifier = mappingInBO.getPropertyUniqueIdentifier();
//            if (ONTOLOGY.getCode().equals(propertyUniqueIdentifier)) {
//
//            } else {
//                ParameterMetadataVO parameterMetadataVO = parameterMetadataMap.get(parameterName);
//                if (parameterMetadataVO == null) {
//                    throw ExceptionFactory.getOntologyFunctionBindingParameterNotFoundException(null);
//                }
//                OntologyPropertyVO ontologyPropertyVO = propertyMap.get(propertyUniqueIdentifier);
//                if (ontologyPropertyVO == null) {
//                    throw ExceptionFactory.getOntologyFunctionMappedPropertyNotFoundException(null);
//                }
//                if (!parameterMetadataVO.getType().equals(ontologyPropertyVO.getPropertyType().name())) {
//                    throw ExceptionFactory.getOntologyFunctionParameterPropertyTypeNotSameException(null);
//                }
//            }
//        }
//    }

    @Override
    public OntologyActionVO getMetadataByApi(String apiName)
            throws OntologyFunctionMappedPropertyNotFoundException,
            OntologyFunctionNotFoundException,
            FunctionClassNotNewInstanceException,
            FunctionFileNotCompiled,
            FunctionNotFoundException {

        OntologyAction ontologyAction = ontologyActionMapper.selectByApi(apiName);
        if (ontologyAction == null) {
            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
        }
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyAction.getOntologyUniqueIdentifier());
        Map<String, OntologyPropertyVO> ontologyPropertiesMap = queryPropertiesByOntologyUniqueIdentifier(ontologyAction.getOntologyUniqueIdentifier());
        List<OntologyActionMappingIn> allMappings = ontologyActionMappingInMapper.selectById(ontologyAction.getId());
        //List<ParameterMetadataVO> parameters = functionService.getParameters(ontologyAction.getFunctionApi());
        List<ParameterMetadataVO> parameters = Lists.newArrayList();
        return getActionVO(ontologyAction, ontologyMeta, allMappings, ontologyPropertiesMap, parameters);
    }

    private Map<String, OntologyPropertyVO> queryPropertiesByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) {
        Map<String, OntologyPropertyVO> ontologyPropertiesMap = new HashMap<>();
//        List<OntologyPropertyVO> ontologyProperties = ontologyPropertyService.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        List<OntologyPropertyVO> ontologyProperties = Lists.newArrayList();
        ontologyProperties.forEach(ontologyProperty -> {
            ontologyPropertiesMap.put(ontologyProperty.getUniqueIdentifier(), ontologyProperty);
        });
        return ontologyPropertiesMap;
    }


    @Override
    public List<ParameterMetadataVO> getParametersByApi(String actionApi)
            throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException, OntologyFunctionNotFoundException {

        return null;
//        OntologyAction ontologyAction = ontologyActionMapper.selectByApi(actionApi);
//        if (ontologyAction == null) {
//            throw ExceptionFactory.getOntologyFunctionNotFoundException(null);
//        }
//        List<ParameterMetadataVO> parameterMetadataVOList = functionService.getParameters(ontologyAction.getFunctionApi());
//        List<OntologyActionMappingIn> mappingIns = ontologyActionMappingInMapper.selectById(ontologyAction.getId());
//        List<String> mappedParameters = mappingIns.stream().map(OntologyActionMappingIn::getParameterName).collect(Collectors.toList());
//        parameterMetadataVOList.removeIf(parameterMetadataVO -> mappedParameters.contains(parameterMetadataVO.getName()));
//        return parameterMetadataVOList;
    }


    @Override
    public List<OntologyActionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) {

        List<OntologyAction> list = ontologyActionMapper.selectByOntologyIdentifier(ontologyUniqueIdentifier);
        return list.stream().map(item -> {
            try {
                return getMetadataByApi(item.getApi());
            } catch (OntologyFunctionMappedPropertyNotFoundException | OntologyFunctionNotFoundException |
                    FunctionClassNotNewInstanceException | FunctionFileNotCompiled | FunctionNotFoundException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    private OntologyActionVO getActionVO(
            OntologyAction action,
            OntologyMeta ontologyMeta,
            List<OntologyActionMappingIn> allMappingIns,
            Map<String, OntologyPropertyVO> ontologyPropertiesMap,
            List<ParameterMetadataVO> parameters)
            throws OntologyFunctionMappedPropertyNotFoundException,
            FunctionClassNotNewInstanceException,
            FunctionFileNotCompiled,
            FunctionNotFoundException {

        return null;

//        if (ontologyMeta == null) {
//            return null;
//        }
//        OntologyActionVO ontologyActionVO = new OntologyActionVO();
//        BeanUtils.copyProperties(action, ontologyActionVO);
//        ontologyActionVO.setOntologyDisplayName(ontologyMeta.getDisplayName());
//
//        List<OntologyActionMappingInVO> mappingInVOs = new ArrayList<>();
//        String ontologyType = StringUtils.capitalize(ontologyMeta.getApiName());
//        for (OntologyActionMappingIn mapping : allMappingIns) {
//            boolean isOntologySelf = ONTOLOGY.getCode().equals(mapping.getPropertyUniqueIdentifier());
//            OntologyActionMappingInVO mappingInVO = new OntologyActionMappingInVO();
//            BeanUtils.copyProperties(mapping, mappingInVO);
//            if (!isOntologySelf) {
//                OntologyPropertyVO ontologyPropertyVO = ontologyPropertiesMap.get(mapping.getPropertyUniqueIdentifier());
//                if (ontologyPropertyVO == null) {
//                    throw ExceptionFactory.getOntologyFunctionMappedPropertyNotFoundException(null);
//                }
//                mappingInVO.setPropertyName(ontologyPropertyVO.getDisplayName());
//                mappingInVO.setPropertyType(ontologyPropertyVO.getPropertyType() != null ? ontologyPropertyVO.getPropertyType().name() : null);
//            } else {
//                mappingInVO.setPropertyName(DEFAULT_OBJECT_DESC);
//                mappingInVO.setPropertyType(ontologyType);
//            }
//            List<ParameterMetadataVO> parameterMetadataVOList = functionService.getParameters(action.getFunctionApi());
//            List<ParameterMetadataVO> matchedParaList = parameterMetadataVOList.stream()
//                    .filter(parameterMetadataVO -> parameterMetadataVO.getName().equals(mappingInVO.getParameterName()))
//                    .collect(Collectors.toList());
//            if (!matchedParaList.isEmpty()) {
//                if (isOntologySelf) {
//                    mappingInVO.setParameterType(ontologyType);
//                } else {
//                    mappingInVO.setParameterType(matchedParaList.get(0).getType());
//                }
//            }
//            mappingInVOs.add(mappingInVO);
//        }
//        ontologyActionVO.setMappingIns(mappingInVOs);
//        List<String> mappedParameters = mappingInVOs.stream().map(OntologyActionMappingIn::getParameterName).collect(Collectors.toList());
//        if (parameters != null) {
//            parameters.removeIf(parameterMetadataVO -> mappedParameters.contains(parameterMetadataVO.getName()));
//            ontologyActionVO.setParameters(parameters);
//        }
//        return ontologyActionVO;
    }

    @Override
    public int delete(long id) {

        return ontologyActionMapper.deleteById(id);
    }

    @Override
    public PageInfo<OntologyActionVO> metaList(Integer page, Integer size) {

        PageHelper.startPage(page, size);
        PageInfo<OntologyAction> pageInfo = new PageInfo<>(ontologyActionMapper.selectList(new QueryWrapper<>()));
        //把这部分代码先注释
        List<OntologyActionVO> collect = pageInfo.getList().stream().map(item -> {
            try {
                return getMetadataByApi(item.getApi());
            } catch (OntologyFunctionMappedPropertyNotFoundException | OntologyFunctionNotFoundException |
                    FunctionClassNotNewInstanceException | FunctionFileNotCompiled | FunctionNotFoundException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        /**
         OntologyAction action = pageInfo.getList().get(0);
         List<OntologyActionVO> collect = new ArrayList<>();
         try {
         OntologyActionVO ontologyActionVO = getMetadataByApi(action.getApi());
         collect.add(ontologyActionVO);
         } catch (exception e) {

         }
         **/
        PageInfo<OntologyActionVO> pageResult = new PageInfo<>(collect);
        BeanUtils.copyProperties(pageInfo, pageResult);
        pageResult.setList(collect);
        return pageResult;
    }


    private DocumentContext simpleCreateAndFill(String jsonSchema, Map<String, Object> values) throws Exception {
        JsonNode nullObj = createAllNull(jsonSchema);
        System.out.println(nullObj);
        DocumentContext context = JsonPath.parse(nullObj.toString());

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            context.set(entry.getKey(), entry.getValue());
        }
        return context;
    }


    private JsonNode createAllNull(String schemaStr) throws Exception {
        JsonNode schema = objectMapper.readTree(schemaStr);
        return buildNullNode(schema);
    }

    private JsonNode buildNullNode(JsonNode schema) {
        if (!schema.has("type")) {
            return NullNode.getInstance();
        }

        String type = schema.get("type").asText();
        switch (type) {
            case "object":
                ObjectNode obj = objectMapper.createObjectNode();
                if (schema.has("properties")) {
                    schema.get("properties").fields().forEachRemaining(field -> {
                        obj.set(field.getKey(), buildNullNode(field.getValue()));
                    });
                }
                return obj;
            case "array":
                return objectMapper.createArrayNode();
            default:
                return NullNode.getInstance();
        }
    }


}
