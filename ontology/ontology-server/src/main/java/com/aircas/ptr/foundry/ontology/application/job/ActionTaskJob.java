package com.aircas.ptr.foundry.ontology.application.job;


import com.aircas.ptr.foundry.ontology.application.service.OntologyActionService;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyActionVO;
import lombok.SneakyThrows;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@DisallowConcurrentExecution
@Component
public class ActionTaskJob implements Job {

    private final static String ACTION_TASK_API = "actionApi";

    private static OntologyActionService ontologyActionService;

    @Autowired
    public void setOntologyActionService(OntologyActionService ontologyActionService) {
        ActionTaskJob.ontologyActionService = ontologyActionService;
    }

    @SneakyThrows
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        String api = (String) context.getMergedJobDataMap().get(ACTION_TASK_API);
        ontologyActionService.handleTask(api);
    }
}
