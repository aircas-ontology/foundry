package com.aircas.ptr.foundry.ontology.mq;

import com.aircas.ptr.foundry.ontology.service.CdcTaskInitService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;




/**
 * 项目启动时自动初始化 CDC 任务
 */
@Slf4j
@Component
public class CdcTaskBuilder {

    @Resource
    private CdcTaskInitService cdcTaskService;
    @PostConstruct
    public void run() {
        log.info("=============== 开始初始化 CDC 任务 ===============");

        try {
            cdcTaskService.initializeCdcTasks();
        } catch (Exception e) {
            log.error("CDC 任务初始化异常", e);
        }
    }
}