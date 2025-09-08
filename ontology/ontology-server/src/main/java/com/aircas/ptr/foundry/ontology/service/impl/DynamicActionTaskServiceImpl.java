package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.job.ActionTaskJob;
import com.aircas.ptr.foundry.ontology.service.DynamicActionTaskService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DynamicActionTaskServiceImpl implements DynamicActionTaskService {


    private final static String ACTION_TASK_GROUP = "DynamicActionTask";
    private final static String ACTION_TASK_TRIGGER = "DynamicActionTaskTrigger";
    private final static String ACTION_TASK_ID = "taskId";
    private final Map<Long, JobKey> tasks = new ConcurrentHashMap<>();

    @Autowired
    private Scheduler scheduler;

    @Override
    public boolean addActionTask(Long taskId, String corn) throws SchedulerException {

        if (tasks.containsKey(taskId)) {
            boolean b = removeActionTask(taskId);
            if (!b) {
                return false;
            }
        }
        JobDetail jobDetail = JobBuilder.newJob(ActionTaskJob.class)
                .withIdentity(taskId.toString(), ACTION_TASK_GROUP)
                .usingJobData(ACTION_TASK_ID, taskId)
                .build();
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(taskId.toString(), ACTION_TASK_TRIGGER)
                .withSchedule(CronScheduleBuilder.cronSchedule(corn))
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
        tasks.put(taskId, jobDetail.getKey());


        return true;
    }

    @Override
    public boolean removeActionTask(Long taskId) throws SchedulerException {

        JobKey jobKey = tasks.remove(taskId);
        if (jobKey != null) {
            return scheduler.deleteJob(jobKey);
        }
        return false;
    }

    @Override
    public Map<Long, JobKey> listActionTask() {

        return Collections.unmodifiableMap(tasks);
    }
}
