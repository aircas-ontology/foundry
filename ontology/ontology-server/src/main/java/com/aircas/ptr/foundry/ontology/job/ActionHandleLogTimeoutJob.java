package com.aircas.ptr.foundry.ontology.job;

import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleLog;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.ActionHandleLogMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 执行日志超时兜底：RULE/TASK/MANUAL 三类日志都是「先插 PENDING、执行完回写结果」，
 * 执行机在执行中途重启/中断会留下永久 PENDING。定时把超时的 PENDING 标 FAILED。
 * <p>多实例共库时各自独立扫，UPDATE 自带 PENDING 条件保证行级原子，重复扫无害；
 * 被误判超时的执行若之后真的完成，执行机自己的回写会覆盖成真实结果。</p>
 */
@Slf4j
@Component
public class ActionHandleLogTimeoutJob {

    @Resource
    private ActionHandleLogMapper actionHandleLogMapper;

    /** PENDING 超过该时长视为执行机已失联；批量树遍历可能较慢，默认给 10 分钟 */
    @Value("${action.handle-log.pending-timeout-ms:600000}")
    private long pendingTimeoutMs;

    @Scheduled(fixedDelayString = "${action.handle-log.pending-sweep-delay-ms:60000}",
            initialDelayString = "${action.handle-log.pending-sweep-delay-ms:60000}")
    public void sweepTimeoutPending() {
        var cutoff = new Date(System.currentTimeMillis() - pendingTimeoutMs);
        int updated = actionHandleLogMapper.update(null, new LambdaUpdateWrapper<ActionHandleLog>()
                .eq(ActionHandleLog::getTaskStatus, TaskStatusEnum.PENDING)
                .lt(ActionHandleLog::getTriggerTime, cutoff)
                .set(ActionHandleLog::getTaskStatus, TaskStatusEnum.FAILED)
                .set(ActionHandleLog::getMsg, "执行超时兜底：超过时限未回写结果，执行机可能已重启/中断")
                .set(ActionHandleLog::getCompleteTime, new Date()));
        if (updated > 0) {
            log.warn("执行日志超时兜底：{} 条 PENDING 标记为 FAILED（超时阈值 {}ms）", updated, pendingTimeoutMs);
        }
    }
}