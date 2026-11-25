package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.EventStageCreateParam;
import com.aircas.ptr.foundry.topic.model.po.EventStage;
import com.baomidou.mybatisplus.spring.service.IService;

public interface EventStageService extends IService<EventStage> {

    Long create(EventStageCreateParam param);
}
