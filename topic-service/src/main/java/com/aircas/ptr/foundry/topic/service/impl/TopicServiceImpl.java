package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.param.TopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.TopicQueryParam;
import com.aircas.ptr.foundry.topic.model.po.Topic;
import com.aircas.ptr.foundry.topic.model.vo.TopicVO;
import com.aircas.ptr.foundry.topic.repository.TopicMapper;
import com.aircas.ptr.foundry.topic.service.TopicService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements TopicService {

    @Transactional
    @Override
    public Integer create(TopicCreateParam param) {
        Topic topic = Topic.builder()
                .name(param.getName())
                .description(param.getDescription())
                .build();
        save(topic);
        return topic.getId();
    }

    @Override
    public TopicVO get(Integer id) {
        return toVO(requireTopic(id));
    }

    @Override
    public IPage<TopicVO> page(TopicQueryParam param) {
        LambdaQueryWrapper<Topic> wrapper = new LambdaQueryWrapper<Topic>()
                .like(StringUtils.isNotBlank(param.getName()), Topic::getName, param.getName())
                .orderByDesc(Topic::getId);
        Page<Topic> page = page(new Page<>(param.getPageNum(), param.getPageSize()), wrapper);
        Page<TopicVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Transactional
    @Override
    public void delete(Integer id) {
        requireTopic(id);
        removeById(id);
    }

    private Topic requireTopic(Integer id) {
        Topic topic = getById(id);
        PreconditionUtils.checkNotNull(topic, "主题不存在", HttpStatus.BAD_REQUEST);
        return topic;
    }

    private TopicVO toVO(Topic topic) {
        return TopicVO.builder()
                .id(topic.getId())
                .name(topic.getName())
                .description(topic.getDescription())
                .createTime(topic.getCreateTime())
                .updateTime(topic.getUpdateTime())
                .build();
    }
}
