package com.aircas.ptr.foundry.ontology.job;

import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleLog;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleTask;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionHandleLogMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionHandleTaskMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

@Component
@Slf4j
public class XxlJobHandler {


    @Resource
    private OntologyActionService actionService;

    @Resource
    private ActionHandleLogMapper actionHandleLogMapper;

    @Resource
    private ActionHandleTaskMapper actionHandleTaskMapper;


    private ObjectMapper objectMapper = new ObjectMapper();


    @XxlJob(Constants.ACTION_EXECUTE_JOB_NAME)
    public void executeOntologyActionJobHandler() throws Exception {
        String jobParam = XxlJobHelper.getJobParam();
        var executeParam = objectMapper.readValue(jobParam, OntologyActionExecuteParam.class);

        var action = actionService.getOne(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getApi, executeParam.getActionApi()));
        var handleTask = actionHandleTaskMapper.selectOne(new LambdaQueryWrapper<ActionHandleTask>().eq(ActionHandleTask::getActionId, action.getId()));
        //插入执行日志
        var handleLog = ActionHandleLog.builder()
                .actionHandleTaskId(handleTask.getId())
                .requestParam(jobParam)
                .triggerTime(new Date())
                .taskStatus(TaskStatusEnum.PENDING)
                .build();
        actionHandleLogMapper.insert(handleLog);
        //执行行为
        try {
            var msg = actionService.executeAction(executeParam);
            handleLog.setMsg(msg).setCompleteTime(new Date()).setTaskStatus(TaskStatusEnum.COMPLETED);
            actionHandleLogMapper.updateById(handleLog);
        } catch (Exception e) {
            log.error("executeOntologyActionJobHandler failed, jobParam:" + jobParam, e);
            handleLog.setCompleteTime(new Date()).setTaskStatus(TaskStatusEnum.FAILED);
            actionHandleLogMapper.updateById(handleLog);
        }

    }
}
