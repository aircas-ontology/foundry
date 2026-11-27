package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.EventTopicOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.po.EventTopicOverview;
import com.baomidou.mybatisplus.spring.service.IService;

public interface EventTopicOverviewService extends IService<EventTopicOverview> {

    Long create(EventTopicOverviewCreateParam param);
}
