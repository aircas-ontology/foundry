package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.DeletedFlagEnum;
import com.aircas.ptr.foundry.topic.model.param.EventTopicOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.po.EventTopic;
import com.aircas.ptr.foundry.topic.model.po.EventTopicOverview;
import com.aircas.ptr.foundry.topic.model.vo.EventTopicOverviewVO;
import com.aircas.ptr.foundry.topic.repository.EventTopicMapper;
import com.aircas.ptr.foundry.topic.repository.EventTopicOverviewMapper;
import com.aircas.ptr.foundry.topic.service.EventTopicOverviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EventTopicOverviewServiceImpl extends ServiceImpl<EventTopicOverviewMapper, EventTopicOverview>
        implements EventTopicOverviewService {

    @Resource
    private EventTopicMapper eventTopicMapper;

    @Transactional
    @Override
    public Long create(EventTopicOverviewCreateParam param) {
        EventTopic eventTopic = eventTopicMapper.selectOne(new LambdaQueryWrapper<EventTopic>()
                .eq(EventTopic::getId, param.getEventTopicId())
                .eq(EventTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkNotNull(eventTopic, "事件主题不存在", HttpStatus.BAD_REQUEST);
        EventTopicOverview overview = EventTopicOverview.builder()
                .eventTopicId(param.getEventTopicId())
                .type(param.getType())
                .name(param.getName())
                .proportion(param.getProportion())
                .quantity(param.getQuantity())
                .creator(StringUtils.isBlank(param.getCreator()) ? "admin" : param.getCreator().trim())
                .build();
        save(overview);
        return overview.getId();
    }

    @Override
    public List<EventTopicOverviewVO> listByEventTopicId(Long eventTopicId) {
        return list(new LambdaQueryWrapper<EventTopicOverview>()
                .eq(EventTopicOverview::getEventTopicId, eventTopicId)
                .orderByAsc(EventTopicOverview::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private EventTopicOverviewVO toVO(EventTopicOverview overview) {
        return EventTopicOverviewVO.builder()
                .id(overview.getId())
                .eventTopicId(overview.getEventTopicId())
                .type(overview.getType())
                .name(overview.getName())
                .proportion(overview.getProportion())
                .quantity(overview.getQuantity())
                .build();
    }
}
