package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.DeletedFlagEnum;
import com.aircas.ptr.foundry.topic.model.enums.EventTopicStatusEnum;
import com.aircas.ptr.foundry.topic.model.param.EventTopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.EventTopicQueryParam;
import com.aircas.ptr.foundry.topic.model.param.EventTopicUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.EventTopic;
import com.aircas.ptr.foundry.topic.model.po.EventTopicOverview;
import com.aircas.ptr.foundry.topic.model.vo.EventTopicOverviewStatVO;
import com.aircas.ptr.foundry.topic.model.vo.EventTopicVO;
import com.aircas.ptr.foundry.topic.repository.EventTopicMapper;
import com.aircas.ptr.foundry.topic.repository.EventTopicOverviewMapper;
import com.aircas.ptr.foundry.topic.service.EventTopicService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventTopicServiceImpl extends ServiceImpl<EventTopicMapper, EventTopic> implements EventTopicService {

    @Resource
    private EventTopicOverviewMapper eventTopicOverviewMapper;

    @Transactional
    @Override
    public Long create(EventTopicCreateParam param) {
        checkTimeRange(param.getStartTime(), param.getEndTime());
        EventTopic eventTopic = EventTopic.builder()
                .eventName(param.getEventName())
                .category(param.getCategory())
                .startTime(param.getStartTime())
                .endTime(param.getEndTime())
                .geoRegion(param.getGeoRegion())
                .regionPoint(param.getRegionPoint())
                .description(param.getDescription())
                .ontologySpace(param.getOntologySpace())
                .status(param.getStatus() == null ? EventTopicStatusEnum.RUNNING.getCode() : param.getStatus())
                .deleted(DeletedFlagEnum.NOT_DELETED.getCode())
                .creator(StringUtils.isBlank(param.getCreator()) ? "admin" : param.getCreator().trim())
                .build();
        save(eventTopic);
        return eventTopic.getId();
    }

    @Transactional
    @Override
    public void delete(String eventTopicCode) {
        List<Long> codes = Arrays.stream(eventTopicCode.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(this::parseCode)
                .distinct()
                .toList();
        PreconditionUtils.checkArgument(!codes.isEmpty(), "事件主题编号不能为空", HttpStatus.BAD_REQUEST);
        long exists = count(new LambdaQueryWrapper<EventTopic>()
                .in(EventTopic::getId, codes)
                .eq(EventTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkArgument(exists == codes.size(), "事件主题不存在", HttpStatus.BAD_REQUEST);
        update(new LambdaUpdateWrapper<EventTopic>()
                .in(EventTopic::getId, codes)
                .eq(EventTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode())
                .set(EventTopic::getDeleted, DeletedFlagEnum.DELETED.getCode()));
    }

    @Override
    public EventTopicVO get(String eventTopicCode) {
        return toVO(requireEventTopic(parseCode(eventTopicCode)));
    }

    @Override
    public IPage<EventTopicVO> page(EventTopicQueryParam param) {
        EventTopicQueryParam query = param == null ? new EventTopicQueryParam() : param;
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        String keyword = StringUtils.trimToEmpty(query.getKeyword());
        LambdaQueryWrapper<EventTopic> wrapper = new LambdaQueryWrapper<EventTopic>()
                .eq(EventTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode())
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(EventTopic::getCategory, keyword)
                        .or().like(EventTopic::getEventName, keyword)
                        .or().like(EventTopic::getGeoRegion, keyword))
                .orderByDesc(EventTopic::getStartTime)
                .orderByAsc(EventTopic::getId);
        Page<EventTopic> page = page(new Page<>(pageNum, pageSize), wrapper);
        Map<Long, List<EventTopicOverviewStatVO>> overviewStats = loadOverviewStats(page.getRecords());
        Page<EventTopicVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(eventTopic -> {
            EventTopicVO vo = toVO(eventTopic);
            vo.setOverviewStats(overviewStats.getOrDefault(eventTopic.getId(), List.of()));
            return vo;
        }).toList());
        return result;
    }

    private Map<Long, List<EventTopicOverviewStatVO>> loadOverviewStats(List<EventTopic> eventTopics) {
        if (eventTopics.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = eventTopics.stream().map(EventTopic::getId).toList();
        List<EventTopicOverview> rows = eventTopicOverviewMapper.selectList(new LambdaQueryWrapper<EventTopicOverview>()
                .in(EventTopicOverview::getEventTopicId, ids)
                .select(EventTopicOverview::getEventTopicId,
                        EventTopicOverview::getType,
                        EventTopicOverview::getName,
                        EventTopicOverview::getQuantity,
                        EventTopicOverview::getProportion));
        Map<Long, Map<StatKey, StatValue>> grouped = new HashMap<>();
        for (EventTopicOverview row : rows) {
            StatKey key = new StatKey(StringUtils.defaultString(row.getType()),
                    StringUtils.defaultString(row.getName()));
            StatValue value = new StatValue(row.getQuantity() == null ? 0 : row.getQuantity(),
                    row.getProportion() == null ? BigDecimal.ZERO : row.getProportion());
            grouped.computeIfAbsent(row.getEventTopicId(), id -> new HashMap<>())
                    .merge(key, value, StatValue::add);
        }
        Map<Long, List<EventTopicOverviewStatVO>> stats = new HashMap<>();
        grouped.forEach((id, counts) -> stats.put(id, counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(StatKey::type).thenComparing(StatKey::name)))
                .map(entry -> EventTopicOverviewStatVO.builder()
                        .type(entry.getKey().type())
                        .name(entry.getKey().name())
                        .quantity(entry.getValue().quantity())
                        .proportion(entry.getValue().proportion())
                        .build())
                .toList()));
        return stats;
    }

    private record StatKey(String type, String name) {
    }

    private record StatValue(int quantity, BigDecimal proportion) {
        private StatValue add(StatValue other) {
            return new StatValue(quantity + other.quantity, proportion.add(other.proportion));
        }
    }

    @Transactional
    @Override
    public void update(EventTopicUpdateParam param) {
        checkTimeRange(param.getStartTime(), param.getEndTime());
        EventTopic eventTopic = requireEventTopic(param.getId());
        eventTopic.setEventName(param.getEventName());
        eventTopic.setCategory(param.getCategory());
        eventTopic.setStartTime(param.getStartTime());
        eventTopic.setEndTime(param.getEndTime());
        eventTopic.setGeoRegion(param.getGeoRegion());
        eventTopic.setRegionPoint(param.getRegionPoint());
        eventTopic.setDescription(param.getDescription());
        eventTopic.setOntologySpace(param.getOntologySpace());
        eventTopic.setStatus(param.getStatus());
        updateById(eventTopic);
    }

    private EventTopic requireEventTopic(Long eventTopicCode) {
        EventTopic eventTopic = getOne(new LambdaQueryWrapper<EventTopic>()
                .eq(EventTopic::getId, eventTopicCode)
                .eq(EventTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkNotNull(eventTopic, "事件主题不存在", HttpStatus.BAD_REQUEST);
        return eventTopic;
    }

    private Long parseCode(String eventTopicCode) {
        PreconditionUtils.checkArgument(eventTopicCode.matches("\\d+"), "事件主题不存在", HttpStatus.BAD_REQUEST);
        return Long.valueOf(eventTopicCode);
    }

    private void checkTimeRange(Date startTime, Date endTime) {
        PreconditionUtils.checkArgument(!endTime.before(startTime), "结束时间不能早于开始时间", HttpStatus.BAD_REQUEST);
    }

    private EventTopicVO toVO(EventTopic eventTopic) {
        return EventTopicVO.builder()
                .id(eventTopic.getId())
                .eventName(eventTopic.getEventName())
                .category(eventTopic.getCategory())
                .startTime(eventTopic.getStartTime())
                .endTime(eventTopic.getEndTime())
                .geoRegion(eventTopic.getGeoRegion())
                .regionPoint(eventTopic.getRegionPoint())
                .description(eventTopic.getDescription())
                .ontologySpace(eventTopic.getOntologySpace())
                .status(eventTopic.getStatus())
                .build();
    }
}
