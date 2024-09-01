package com.aircas.ptr.foundry.ontology.application.job;


import com.aircas.ptr.foundry.ontology.application.service.OntologyActionService;
import lombok.SneakyThrows;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@DisallowConcurrentExecution
@Component
public class ActionTaskJob implements Job {

    private final static String ACTION_TASK_ID = "taskId";

    private static OntologyActionService ontologyActionService;

    @Autowired
    public void setOntologyActionService(OntologyActionService ontologyActionService) {
        ActionTaskJob.ontologyActionService = ontologyActionService;
    }

    @SneakyThrows
    @Override
    public void execute(JobExecutionContext context) {

        Long taskId = (Long) context.getMergedJobDataMap().get(ACTION_TASK_ID);
        ontologyActionService.handleTask(taskId);
    }
}
