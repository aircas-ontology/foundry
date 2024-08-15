package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.ontology.application.job.ActionTaskJob;
import com.aircas.ptr.foundry.ontology.application.service.DynamicActionTaskService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DynamicActionTaskServiceImpl implements DynamicActionTaskService {


    private final static String ACTION_TASK_GROUP = "DynamicActionTask";
    private final static String ACTION_TASK_TRIGGER = "DynamicActionTaskTrigger";
    private final static String ACTION_TASK_API = "actionApi";
    private Map<String, JobKey> tasks = new ConcurrentHashMap<>();

    @Autowired
    private Scheduler scheduler;

    @Override
    public boolean addActionTask(String taskApi, String corn) throws SchedulerException {

        if (tasks.containsKey(taskApi)) {
            boolean b = removeActionTask(taskApi);
            if (!b) {
                return false;
            }
        }
        JobDetail jobDetail = JobBuilder.newJob(ActionTaskJob.class)
                .withIdentity(taskApi.toString(), ACTION_TASK_GROUP)
                .usingJobData(ACTION_TASK_API, taskApi)
                .build();
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(taskApi.toString(), ACTION_TASK_TRIGGER)
                .withSchedule(CronScheduleBuilder.cronSchedule(corn))
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
        tasks.put(taskApi, jobDetail.getKey());

        return true;
    }

    @Override
    public boolean removeActionTask(String taskApi) throws SchedulerException {

        JobKey jobKey = tasks.remove(taskApi);
        if (jobKey != null) {
            return scheduler.deleteJob(jobKey);
        }
        return false;
    }
}
