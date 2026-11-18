package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleLog;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionHandleLogMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.service.ManualExecuteLogService;
import com.aircas.ptr.foundry.ontology.utils.ExecutorMachineInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Service
public class ManualExecuteLogServiceImpl implements ManualExecuteLogService {

    @Resource
    private ActionHandleLogMapper actionHandleLogMapper;

    @Resource
    private OntologyActionMapper ontologyActionMapper;

    /** 用应用配好的 mapper：执行结果里有日期和脚本返回的杂类，裸 mapper 序列化不了；字段名避开 objectMapper，否则会命中数据湖同名 mapper bean */
    @Resource
    private ObjectMapper jacksonObjectMapper;

    @Override
    public <T> T runAndLog(Integer ontologySpaceId, String actionApi, Object requestParam,
                           Supplier<T> execution, Function<T, String> messageOf) {
        var action = ontologyActionMapper.selectOne(new LambdaQueryWrapper<OntologyAction>()
                .select(OntologyAction::getId)
                .eq(OntologyAction::getApi, actionApi)
                .eq(OntologyAction::getOntologySpaceId, ontologySpaceId));
        if (action == null) {
            // 无效 api 连行为都定位不到，交给执行体报错，不记一条没有归属的日志
            return execution.get();
        }
        var handleLog = ActionHandleLog.builder()
                .actionHandleId(action.getId())
                .actionHandleType(ActionSchedulingTypeEnum.MANUAL)
                .requestParam(writeJson(requestParam))
                .triggerTime(new Date())
                .executorIp(ExecutorMachineInfo.ip())
                .executorHost(ExecutorMachineInfo.host())
                .taskStatus(TaskStatusEnum.PENDING)
                .build();
        // 日志独立提交，不能被执行体的事务回滚带走
        actionHandleLogMapper.insert(handleLog);
        try {
            handleLog.setExecuteStartTime(new Date());
            var result = execution.get();
            handleLog.setMsg(messageOf.apply(result))
                    .setExecuteResult(writeJson(result))
                    .setCompleteTime(new Date())
                    .setTaskStatus(TaskStatusEnum.COMPLETED);
            actionHandleLogMapper.updateById(handleLog);
            return result;
        } catch (Exception e) {
            log.error("手动执行行为失败, actionApi={}, ontologySpaceId={}", actionApi, ontologySpaceId, e);
            handleLog.setMsg(e.getMessage())
                    .setCompleteTime(new Date())
                    .setTaskStatus(TaskStatusEnum.FAILED);
            actionHandleLogMapper.updateById(handleLog);
            throw e;
        }
    }

    /** 记日志不能反过来改变执行结论：结果已经返回给调用方，序列化不了就只留个空明细 */
    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return jacksonObjectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("手动执行日志序列化失败, valueClass={}", value.getClass().getName(), e);
            return null;
        }
    }
}
