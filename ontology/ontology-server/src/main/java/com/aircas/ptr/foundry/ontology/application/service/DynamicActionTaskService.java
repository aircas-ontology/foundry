package com.aircas.ptr.foundry.ontology.application.service;

import org.quartz.SchedulerException;

public interface DynamicActionTaskService {

    boolean addActionTask(Long taskId, String corn) throws SchedulerException;

    boolean removeActionTask(Long taskId) throws SchedulerException;
}
