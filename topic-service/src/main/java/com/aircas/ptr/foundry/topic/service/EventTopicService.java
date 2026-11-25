package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.EventTopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.EventTopicQueryParam;
import com.aircas.ptr.foundry.topic.model.param.EventTopicUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.EventTopic;
import com.aircas.ptr.foundry.topic.model.vo.EventTopicVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;

public interface EventTopicService extends IService<EventTopic> {

    Long create(EventTopicCreateParam param);

    void delete(String eventTopicCode);

    EventTopicVO get(String eventTopicCode);

    IPage<EventTopicVO> page(EventTopicQueryParam param);

    void update(EventTopicUpdateParam param);
}
