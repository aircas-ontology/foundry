package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.DeletedFlagEnum;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicQueryParam;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.TaskTopic;
import com.aircas.ptr.foundry.topic.model.vo.TaskTopicDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.TaskTopicVO;
import com.aircas.ptr.foundry.topic.repository.TaskTopicMapper;
import com.aircas.ptr.foundry.topic.service.TaskTopicService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
public class TaskTopicServiceImpl extends ServiceImpl<TaskTopicMapper, TaskTopic> implements TaskTopicService {

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
                .status(param.getStatus())
                .creator(StringUtils.isBlank(param.getCreator()) ? "admin" : param.getCreator().trim())
                .deleted(DeletedFlagEnum.NOT_DELETED.getCode())
                .build();
        save(taskTopic);
        return taskTopic.getTaskCode();
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
                .in(TaskTopic::getTaskCode, taskCodes)
                .eq(TaskTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkArgument(exists == taskCodes.size(), "任务不存在", HttpStatus.BAD_REQUEST);
        update(new LambdaUpdateWrapper<TaskTopic>()
                .in(TaskTopic::getTaskCode, taskCodes)
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
                .orderByAsc(TaskTopic::getTaskCode));
        return tasks.stream().map(this::toVO).toList();
    }

    @Transactional
    @Override
    public void update(TaskTopicUpdateParam param) {
        checkTimeRange(param.getStartTime(), param.getEndTime());
        TaskTopic taskTopic = requireTask(param.getTaskCode());
        taskTopic.setTaskName(param.getTaskName());
        taskTopic.setTaskType(param.getTaskType());
        taskTopic.setTaskRegion(param.getTaskRegion());
        taskTopic.setRegionPoint(param.getRegionPoint());
        taskTopic.setStartTime(param.getStartTime());
        taskTopic.setEndTime(param.getEndTime());
        taskTopic.setDescription(param.getDescription());
        taskTopic.setOntologySpace(param.getOntologySpace());
        taskTopic.setStatus(param.getStatus());
        updateById(taskTopic);
    }

    private TaskTopic requireTask(Long taskCode) {
        TaskTopic taskTopic = getOne(new LambdaQueryWrapper<TaskTopic>()
                .eq(TaskTopic::getTaskCode, taskCode)
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

    private TaskTopicDetailVO toDetailVO(TaskTopic taskTopic) {
        return TaskTopicDetailVO.builder()
                .taskCode(taskTopic.getTaskCode())
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
                .build();
    }

    private TaskTopicVO toVO(TaskTopic taskTopic) {
        return TaskTopicVO.builder()
                .taskCode(taskTopic.getTaskCode())
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
                .seaCount(taskTopic.getSeaCount())
                .airCount(taskTopic.getAirCount())
                .landCount(taskTopic.getLandCount())
                .eventCount(taskTopic.getEventCount())
                .build();
    }
}
