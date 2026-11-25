package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.TopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.TopicQueryParam;
import com.aircas.ptr.foundry.topic.model.po.Topic;
import com.aircas.ptr.foundry.topic.model.vo.TopicVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;

public interface TopicService extends IService<Topic> {

    Integer create(TopicCreateParam param);

    TopicVO get(Integer id);

    IPage<TopicVO> page(TopicQueryParam param);

    void delete(Integer id);
}
