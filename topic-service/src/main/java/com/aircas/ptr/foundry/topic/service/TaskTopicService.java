package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.TaskTopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicQueryParam;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.TaskTopic;
import com.aircas.ptr.foundry.topic.model.vo.TaskTopicDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.TaskTopicVO;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

public interface TaskTopicService extends IService<TaskTopic> {

    String create(TaskTopicCreateParam param);

    void delete(String taskCode);

    TaskTopicDetailVO get(String taskCode);

    List<TaskTopicVO> list(TaskTopicQueryParam param);

    void update(TaskTopicUpdateParam param);
}
