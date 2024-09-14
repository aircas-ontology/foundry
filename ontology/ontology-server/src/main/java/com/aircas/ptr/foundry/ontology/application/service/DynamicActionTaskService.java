package com.aircas.ptr.foundry.ontology.application.service;

import org.quartz.JobKey;
import org.quartz.SchedulerException;

import java.util.Map;

public interface DynamicActionTaskService {

    boolean addActionTask(Long taskId, String corn) throws SchedulerException;

    boolean removeActionTask(Long taskId) throws SchedulerException;

    Map<Long, JobKey> listActionTask();
}
