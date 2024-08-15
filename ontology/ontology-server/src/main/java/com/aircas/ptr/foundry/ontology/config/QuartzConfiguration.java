package com.aircas.ptr.foundry.ontology.config;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Configuration
public class QuartzConfiguration {

    @Bean
    public Scheduler scheduler() throws SchedulerException {

        SchedulerFactory factory = new StdSchedulerFactory();
        Scheduler scheduler = factory.getScheduler();
        scheduler.start();
        return scheduler;
    }

    @Bean
    public ScheduledExecutorService scheduledExecutorService() {

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10);
        return executor;
    }
}
