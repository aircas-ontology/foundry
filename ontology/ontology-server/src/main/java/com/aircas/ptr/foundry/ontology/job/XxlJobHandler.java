package com.aircas.ptr.foundry.ontology.job;

import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.param.XxlJobActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleLog;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionHandleLogMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionHandleTaskMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
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
        var shardIndex = XxlJobHelper.getShardIndex();
        var shardTotal = XxlJobHelper.getShardTotal();

        String jobParam = XxlJobHelper.getJobParam();
        log.info("收到执行任务请求，参数：{}, shardTotal:{}, shardIndex:{}", jobParam, shardTotal, shardIndex);
        ActionHandleLog handleLog = null;

        //执行行为
        try {
            var executeParam = objectMapper.readValue(jobParam, XxlJobActionExecuteParam.class);
            //插入执行日志
            handleLog = ActionHandleLog.builder()
                    .actionHandleId(executeParam.getScheduleId())
                    .actionHandleType(ActionSchedulingTypeEnum.TASK)
                    .requestParam(jobParam)
                    .triggerTime(new Date())
                    .taskStatus(TaskStatusEnum.PENDING)
                    .build();
            actionHandleLogMapper.insert(handleLog);
            var msg = actionService.executeAction(executeParam.setShardIndex(shardIndex).setShardTotal(shardTotal));
            handleLog.setMsg(msg).setCompleteTime(new Date()).setTaskStatus(TaskStatusEnum.COMPLETED);
            actionHandleLogMapper.updateById(handleLog);
            log.info("任务执行成功" + jobParam);
        } catch (Exception e) {
            log.error("executeOntologyActionJobHandler failed, jobParam:" + jobParam, e);
            handleLog.setCompleteTime(new Date()).setTaskStatus(TaskStatusEnum.FAILED);
            actionHandleLogMapper.updateById(handleLog);
        }

    }
}
