package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.client.XxlJobClient;
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
import com.google.common.collect.Lists;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OntologyActionServiceImpl extends ServiceImpl<OntologyActionMapper, OntologyAction> implements OntologyActionService {


    @Resource
    private ActionHandleLogMapper actionHandleLogMapper;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private OntologyLinkGroupMapper linkGroupMapper;

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private FunctionParamMapper functionParamMapper;

    @Resource
    private OntologyActionLinkMapper actionLinkMapper;

    @Resource
    private OntologyActionMapper actionMapper;

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
            var ontologyLink = linkGroupMapper.selectOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, link.getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(ontologyLink != null && (ontologyLink.getOntologyUniqueIdentifierFrom().equals(ontologyId) || ontologyLink.getOntologyUniqueIdentifierTo().equals(ontologyId)),
                    "无效的本体关系：" + link.getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);
            var actionLink = actionLinkMapper.selectOne(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyLinkUniqueIdentifier, link.getOntologyLinkUniqIdentifier()));
            PreconditionUtils.checkArgument(actionLink == null, "该本体关系已被其他行为关联：" + link.getOntologyLinkUniqIdentifier(), HttpStatus.BAD_REQUEST);

            var output = functionParams.stream().filter(p -> p.getCategory().equals(FunctionParamCategoryEnum.OUTPUT)).findFirst().orElse(null);
            PreconditionUtils.checkArgument(output != null, "函数无输出参数,functionId:" + func.getId());
            actionLinkMapper.insert(OntologyActionLink.builder()
                    .ontologyActionId(ontologyAction.getId())
                    .ontologyLinkParamExpression(link.getOntologyLinkFunctionParamExpression())
                    .ontologyLinkUniqueIdentifier(link.getOntologyLinkUniqIdentifier())
                    .build());
        }

        //校验函数参数
        var paramMap = functionParams.stream().collect(Collectors.toMap(v -> v.getId(), v -> v));

        if (MapUtils.isNotEmpty(paramMap)) {
            List<ActionParamMappingCreateParam> mappingIns = CollectionUtils.isEmpty(param.getMappingIns()) ?
                    new ArrayList<>() : param.getMappingIns();
            List<OntologyActionMappingIn> list = Lists.newArrayList();
            mappingIns.forEach(mapping -> {
                var propertyId = mapping.getPropertyUniqueIdentifier();
                //本体属性是否存在，函数参数是否存在
                var prop = ontologyPropertyService.getOne(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getUniqueIdentifier, propertyId));
                PreconditionUtils.checkArgument(prop != null && paramMap.keySet().contains(mapping.getFunctionParamId()), "无效的行为参数：" + propertyId, HttpStatus.BAD_REQUEST);
                //行为属性类型与函数参数类型是否匹配
                var paramPO = paramMap.get(mapping.getFunctionParamId());
                if (paramPO.getCategory().equals(FunctionParamCategoryEnum.INPUT)) {
                    var dataType = OntologyDataTypeEnum.valueOfDataType(paramPO.getParamType());
                    PreconditionUtils.checkArgument(dataType.equals(OntologyDataTypeEnum.Array) || prop.getPropertyType().equals(dataType), "行为属性类型与函数参数类型不匹配：", HttpStatus.BAD_REQUEST);
                }
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
                .eq(ActionHandleRule::getActionId, action.getId()));
        var actionHandleTask = actionHandleTaskService.getOne(new LambdaQueryWrapper<ActionHandleTask>()
                .eq(ActionHandleTask::getActionId, action.getId()));
        PreconditionUtils.checkArgument(actionHandleRule == null && actionHandleTask == null, "该行为在调度中，不能直接删除", HttpStatus.BAD_REQUEST);
        actionLinkMapper.delete(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyActionId, action.getId()));
        remove(new LambdaQueryWrapper<OntologyAction>().in(OntologyAction::getId, action.getId()));
        ontologyActionMappingInService.remove(new LambdaQueryWrapper<OntologyActionMappingIn>().in(OntologyActionMappingIn::getOntologyActionId, action.getId()));
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

            var propUniqIds = mappings.stream().map(v -> v.getPropertyUniqueIdentifier()).collect(Collectors.toList());
            var props = ontologyPropertyService.list(new LambdaQueryWrapper<OntologyProperty>().in(OntologyProperty::getUniqueIdentifier, propUniqIds));
            var propMap = props.stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v.getOntologyUniqueIdentifier()));

            var mappingVOS = mappings.stream().map(v -> ActionParamMappingVO.builder()
                            .functionParamExpression(v.getFunctionParamExpression())
                            .functionParamId(v.getFunctionParamId())
                            .propertyUniqueIdentifier(v.getPropertyUniqueIdentifier())
                            .ontologyUniqueIdentifier(propMap.get(v.getPropertyUniqueIdentifier()))
                            .build())
                    .collect(Collectors.toList());
            detailVO.setMappingIns(mappingVOS);
        }
        return detailVO;
    }

    @SneakyThrows
    @Override
    public String executeAction(OntologyActionExecuteParam param) {

        var contextInfoDTO = entityService.initActionContextInfoDTO(EntityActionExecuteParam.builder()
                .actionApi(param.getActionApi())
                .ontologyUniqueIdentifier(param.getOntologyUniqueIdentifier())
                .build());
        //获取实体主键列表
        var primaryProperty = contextInfoDTO.getOntologyProperties().stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst().orElse(null);
        //主键数据源未绑定
        if (primaryProperty == null || StringUtils.isEmpty(primaryProperty.getDatasourceColumnName())) {
            return "";
        }
        //查询本体的实体主键
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
        //获取行为关联关系下的本体的所有实体详情
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

    @Override
    @Transactional
    public Long createScheduling(ActionSchedulingCreateParam param) {
        //todo 目前只考虑定时调度
        var schedulingType = param.getType();
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, param.getActionApi()));
        PreconditionUtils.checkNotNull(action, "action不存在：" + param.getActionApi());
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
        var action = actionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, param.getActionApi()));
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
