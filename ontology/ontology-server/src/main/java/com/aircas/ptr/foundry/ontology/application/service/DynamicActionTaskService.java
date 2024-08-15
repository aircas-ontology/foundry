package com.aircas.ptr.foundry.ontology.application.service;

import org.quartz.SchedulerException;

public interface DynamicActionTaskService {

    boolean addActionTask(String taskApi, String corn) throws SchedulerException;

    boolean removeActionTask(String taskApi) throws SchedulerException;
}
