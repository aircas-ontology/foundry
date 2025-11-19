package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.ActionRuleConnectType;
import com.aircas.ptr.foundry.ontology.exception.*;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyActionBo;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyActionMappingInBO;
import com.aircas.ptr.foundry.ontology.model.param.ActionHandleMappingInParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionHandleRuleAddParam;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OntologyActionServiceImpl extends ServiceImpl<OntologyActionMapper, OntologyAction> implements OntologyActionService {

    @Resource
    private OntologyActionMapper ontologyActionMapper;

    @Resource
    private OntologyActionMappingInMapper ontologyActionMappingInMapper;

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

    private final static String DEFAULT_OBJECT_DESC = "当前本体对象";


    @Override
    public void removeByOntologyIdentifier(String ontologyIdentifier) {
        var actions = list(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyUniqueIdentifier, ontologyIdentifier));
        if (CollectionUtils.isNotEmpty(actions)) {
            var actionIds = actions.stream().map(v -> v.getId()).collect(Collectors.toList());
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
        List<ParameterMetadataVO> parameters = functionService.getParameters(ontologyAction.getFunctionApi());
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

}
