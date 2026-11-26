package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.DeletedFlagEnum;
import com.aircas.ptr.foundry.topic.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicQueryParam;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.EventAgg;
import com.aircas.ptr.foundry.topic.model.po.EventStage;
import com.aircas.ptr.foundry.topic.model.po.TaskTopic;
import com.aircas.ptr.foundry.topic.model.vo.EventAggVO;
import com.aircas.ptr.foundry.topic.model.vo.EventStageVO;
import com.aircas.ptr.foundry.topic.model.vo.TaskTopicDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.TaskTopicVO;
import com.aircas.ptr.foundry.topic.repository.EventAggMapper;
import com.aircas.ptr.foundry.topic.repository.EventStageMapper;
import com.aircas.ptr.foundry.topic.repository.TaskTopicMapper;
import com.aircas.ptr.foundry.topic.service.TaskTopicService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TaskTopicServiceImpl extends ServiceImpl<TaskTopicMapper, TaskTopic> implements TaskTopicService {

    private static final ObjectMapper TASK_TYPE_STAT_MAPPER = new ObjectMapper();

    @Resource
    private EventStageMapper eventStageMapper;

    @Resource
    private EventAggMapper eventAggMapper;

    @Transactional
    @Override
    public Long create(TaskTopicCreateParam param) {
        checkTimeRange(param.getStartTime(), param.getEndTime());
        TaskTopic taskTopic = TaskTopic.builder()
                .taskName(param.getTaskName())
                .taskType(param.getTaskType())
                .taskRegion(param.getTaskRegion())
                .regionPoint(param.getRegionPoint())
                .startTime(param.getStartTime())
                .endTime(param.getEndTime())
                .description(param.getDescription())
                .ontologySpace(param.getOntologySpace())
                .status(param.getStatus() == null ? TaskStatusEnum.RUNNING.getCode() : param.getStatus())
                .creator(StringUtils.isBlank(param.getCreator()) ? "admin" : param.getCreator().trim())
                .deleted(DeletedFlagEnum.NOT_DELETED.getCode())
                .build();
        save(taskTopic);
        return taskTopic.getId();
    }

    @Transactional
    @Override
    public void delete(String taskCode) {
        List<Long> taskCodes = Arrays.stream(taskCode.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(this::parseTaskCode)
                .distinct()
                .toList();
        PreconditionUtils.checkArgument(!taskCodes.isEmpty(), "任务编号不能为空", HttpStatus.BAD_REQUEST);
        long exists = count(new LambdaQueryWrapper<TaskTopic>()
                .in(TaskTopic::getId, taskCodes)
                .eq(TaskTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkArgument(exists == taskCodes.size(), "任务不存在", HttpStatus.BAD_REQUEST);
        update(new LambdaUpdateWrapper<TaskTopic>()
                .in(TaskTopic::getId, taskCodes)
                .eq(TaskTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode())
                .set(TaskTopic::getDeleted, DeletedFlagEnum.DELETED.getCode()));
    }

    @Override
    public TaskTopicDetailVO get(String taskCode) {
        return toDetailVO(requireTask(parseTaskCode(taskCode)));
    }

    @Override
    public List<TaskTopicVO> list(TaskTopicQueryParam param) {
        TaskTopicQueryParam query = param == null ? new TaskTopicQueryParam() : param;
        List<TaskTopic> tasks = list(new LambdaQueryWrapper<TaskTopic>()
                .like(StringUtils.isNotBlank(query.getTaskName()), TaskTopic::getTaskName, query.getTaskName())
                .eq(StringUtils.isNotBlank(query.getTaskType()), TaskTopic::getTaskType, query.getTaskType())
                .eq(StringUtils.isNotBlank(query.getTaskRegion()), TaskTopic::getTaskRegion, query.getTaskRegion())
                .ge(query.getStartTime() != null, TaskTopic::getStartTime, query.getStartTime())
                .le(query.getEndTime() != null, TaskTopic::getEndTime, query.getEndTime())
                .eq(TaskTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode())
                .orderByDesc(TaskTopic::getStartTime)
                .orderByAsc(TaskTopic::getId));
        return tasks.stream().map(this::toVO).toList();
    }

    @Transactional
    @Override
    public void update(TaskTopicUpdateParam param) {
        checkTimeRange(param.getStartTime(), param.getEndTime());
        checkTaskTypeStat(param.getTaskTypeStat());
        TaskTopic taskTopic = requireTask(param.getId());
        taskTopic.setTaskName(param.getTaskName());
        taskTopic.setTaskType(param.getTaskType());
        taskTopic.setTaskRegion(param.getTaskRegion());
        taskTopic.setRegionPoint(param.getRegionPoint());
        taskTopic.setStartTime(param.getStartTime());
        taskTopic.setEndTime(param.getEndTime());
        taskTopic.setDescription(param.getDescription());
        taskTopic.setOntologySpace(param.getOntologySpace());
        taskTopic.setStatus(param.getStatus());
        taskTopic.setTaskTypeStat(param.getTaskTypeStat());
        taskTopic.setTargetCount(param.getTargetCount());
        taskTopic.setEventCount(param.getEventCount());
        updateById(taskTopic);
    }

    private TaskTopic requireTask(Long taskCode) {
        TaskTopic taskTopic = getOne(new LambdaQueryWrapper<TaskTopic>()
                .eq(TaskTopic::getId, taskCode)
                .eq(TaskTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkNotNull(taskTopic, "任务不存在", HttpStatus.BAD_REQUEST);
        return taskTopic;
    }

    private Long parseTaskCode(String taskCode) {
        PreconditionUtils.checkArgument(taskCode.matches("\\d+"), "任务不存在", HttpStatus.BAD_REQUEST);
        return Long.valueOf(taskCode);
    }

    private void checkTimeRange(Date startTime, Date endTime) {
        PreconditionUtils.checkArgument(!endTime.before(startTime), "任务结束时间不能早于开始时间", HttpStatus.BAD_REQUEST);
    }

    private void checkTaskTypeStat(String taskTypeStat) {
        JsonNode node;
        try {
            node = TASK_TYPE_STAT_MAPPER.readTree(taskTypeStat);
        } catch (Exception ex) {
            node = null;
        }
        PreconditionUtils.checkArgument(node != null && node.isArray(), "任务类型统计格式不正确", HttpStatus.BAD_REQUEST);
        for (JsonNode item : node) {
            boolean valid = item.isObject()
                    && item.hasNonNull("name") && item.get("name").isTextual()
                    && item.has("count") && item.get("count").isIntegralNumber();
            PreconditionUtils.checkArgument(valid, "任务类型统计格式不正确", HttpStatus.BAD_REQUEST);
        }
    }

    private TaskTopicDetailVO toDetailVO(TaskTopic taskTopic) {
        return TaskTopicDetailVO.builder()
                .id(taskTopic.getId())
                .taskName(taskTopic.getTaskName())
                .taskType(taskTopic.getTaskType())
                .taskRegion(taskTopic.getTaskRegion())
                .regionPoint(taskTopic.getRegionPoint())
                .startTime(taskTopic.getStartTime())
                .endTime(taskTopic.getEndTime())
                .description(taskTopic.getDescription())
                .ontologySpace(taskTopic.getOntologySpace())
                .eventStages(listEventStages(taskTopic.getId()))
                .build();
    }

    private List<EventStageVO> listEventStages(Long taskCode) {
        List<EventStage> stages = eventStageMapper.selectList(new LambdaQueryWrapper<EventStage>()
                .eq(EventStage::getTaskCode, taskCode)
                .eq(EventStage::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode())
                .orderByAsc(EventStage::getStartTime)
                .orderByAsc(EventStage::getId));
        if (stages.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<EventAggVO>> eventsByStage = listEventAggs(stages.stream().map(EventStage::getId).toList());
        return stages.stream()
                .map(stage -> toEventStageVO(stage, eventsByStage.getOrDefault(stage.getId(), Collections.emptyList())))
                .toList();
    }

    private Map<Long, List<EventAggVO>> listEventAggs(List<Long> stageCodes) {
        return eventAggMapper.selectList(new LambdaQueryWrapper<EventAgg>()
                        .in(EventAgg::getStageId, stageCodes)
                        .orderByAsc(EventAgg::getEventTime)
                        .orderByAsc(EventAgg::getId))
                .stream()
                .collect(Collectors.groupingBy(EventAgg::getStageId, LinkedHashMap::new,
                        Collectors.mapping(this::toEventAggVO, Collectors.toList())));
    }

    private EventStageVO toEventStageVO(EventStage stage, List<EventAggVO> events) {
        return EventStageVO.builder()
                .id(stage.getId())
                .taskCode(stage.getTaskCode())
                .stageName(stage.getStageName())
                .startTime(stage.getStartTime())
                .endTime(stage.getEndTime())
                .eventCount(stage.getEventCount())
                .eventCodes(stage.getEventCodes())
                .events(events)
                .build();
    }

    private EventAggVO toEventAggVO(EventAgg event) {
        return EventAggVO.builder()
                .id(event.getId())
                .eventNo(event.getEventNo())
                .eventTitle(event.getEventTitle())
                .eventTime(event.getEventTime())
                .status(event.getStatus())
                .process(event.getProcess())
                .location(event.getLocation())
                .eventLon(event.getEventLon())
                .eventLat(event.getEventLat())
                .sourceType(event.getSourceType())
                .sourcePlatform(event.getSourcePlatform())
                .confidence(event.getConfidence())
                .eventContent(event.getEventContent())
                .ontologyIdentifier(event.getOntologyUniqueIdentifier())
                .instanceId(event.getInstanceId())
                .ontologyDisplayName(event.getOntologyDisplayName())
                .build();
    }

    private TaskTopicVO toVO(TaskTopic taskTopic) {
        return TaskTopicVO.builder()
                .id(taskTopic.getId())
                .taskName(taskTopic.getTaskName())
                .taskType(taskTopic.getTaskType())
                .taskRegion(taskTopic.getTaskRegion())
                .regionPoint(taskTopic.getRegionPoint())
                .startTime(taskTopic.getStartTime())
                .endTime(taskTopic.getEndTime())
                .description(taskTopic.getDescription())
                .ontologySpace(taskTopic.getOntologySpace())
                .status(taskTopic.getStatus())
                .creator(taskTopic.getCreator())
                .createTime(taskTopic.getCreateTime())
                .targetCount(taskTopic.getTargetCount())
                .taskTypeStat(taskTopic.getTaskTypeStat())
                .eventCount(taskTopic.getEventCount())
                .build();
    }
}
