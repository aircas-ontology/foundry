package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.DeletedFlagEnum;
import com.aircas.ptr.foundry.topic.model.param.EventTopicOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.po.EventTopic;
import com.aircas.ptr.foundry.topic.model.po.EventTopicOverview;
import com.aircas.ptr.foundry.topic.model.vo.EventTopicOverviewStatVO;
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

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public List<EventTopicOverviewStatVO> listStats() {
        Map<String, StatValue> grouped = new HashMap<>();
        for (EventTopicOverview row : list()) {
            String type = StringUtils.defaultString(row.getType());
            StatValue value = new StatValue(StringUtils.defaultString(row.getName()),
                    row.getQuantity() == null ? 0 : row.getQuantity(),
                    row.getProportion() == null ? BigDecimal.ZERO : row.getProportion());
            grouped.merge(type, value, StatValue::add);
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> EventTopicOverviewStatVO.builder()
                        .type(entry.getKey())
                        .name(entry.getValue().name())
                        .quantity(entry.getValue().quantity())
                        .proportion(entry.getValue().proportion())
                        .build())
                .toList();
    }

    private record StatValue(String name, int quantity, BigDecimal proportion) {
        private StatValue add(StatValue other) {
            return new StatValue(name, quantity + other.quantity, proportion.add(other.proportion));
        }
    }
}
