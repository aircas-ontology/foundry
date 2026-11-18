package com.aircas.ptr.foundry.ontology.job;

import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.XxlJobActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleLog;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionHandleLogMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionHandleTaskMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.aircas.ptr.foundry.ontology.utils.ExecutorMachineInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
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


    private ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


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
            // 入参：调度存了实体执行参数（entity_json）就记它，那才是真正参与执行的入参；否则记调度参数
            var requestParam = jobParam;
            String entityJson = null;
            if (executeParam.getScheduleId() != null) {
                var scheduledTask = actionHandleTaskMapper.selectById(executeParam.getScheduleId());
                if (scheduledTask != null && StringUtils.isNotBlank(scheduledTask.getEntityJson())) {
                    entityJson = scheduledTask.getEntityJson();
                    requestParam = entityJson;
                }
            }
            log.info("行为调度执行入参：{}", requestParam);
            XxlJobHelper.log("行为调度执行入参：{}", requestParam);
            //插入执行日志
            handleLog = ActionHandleLog.builder()
                    .actionHandleId(executeParam.getScheduleId())
                    .actionHandleType(ActionSchedulingTypeEnum.TASK)
                    .requestParam(requestParam)
                    // xxl-job 2.3.0 执行器上下文拿不到调度中心触发时刻，以执行器收到触发请求的时间为准
                    .triggerTime(new Date())
                    .executorIp(ExecutorMachineInfo.ip())
                    .executorHost(ExecutorMachineInfo.host())
                    .taskStatus(TaskStatusEnum.PENDING)
                    .build();
            actionHandleLogMapper.insert(handleLog);
            handleLog.setExecuteStartTime(new Date());
            if (entityJson != null) {
                // 存了实体执行参数的调度走行为测试：支持指定实例、外部入参和批量组合
                // 执行记录上面已按 TASK 插过一条，这里走不记手动日志的入口，避免同一次执行两条记录
                var testParam = objectMapper.readValue(entityJson, ActionTestParam.class);
                var testResult = actionService.testActionForScheduling(testParam);
                var resultJson = objectMapper.writeValueAsString(testResult);
                log.info("行为调度执行结果：{}", resultJson);
                XxlJobHelper.log("行为调度执行结果：{}", resultJson);
                handleLog.setMsg(testResult.getMessage()).setExecuteResult(resultJson);
            } else {
                var msg = actionService.executeActionForScheduling(executeParam.setShardIndex(shardIndex).setShardTotal(shardTotal));
                handleLog.setMsg(msg);
            }
            handleLog.setCompleteTime(new Date()).setTaskStatus(TaskStatusEnum.COMPLETED);
            actionHandleLogMapper.updateById(handleLog);
            log.info("任务执行成功" + jobParam);
        } catch (Exception e) {
            log.error("executeOntologyActionJobHandler failed, jobParam:" + jobParam, e);
            if (handleLog != null) {
                handleLog.setMsg(e.getMessage()).setCompleteTime(new Date()).setTaskStatus(TaskStatusEnum.FAILED);
                actionHandleLogMapper.updateById(handleLog);
            }
        }

    }
}
