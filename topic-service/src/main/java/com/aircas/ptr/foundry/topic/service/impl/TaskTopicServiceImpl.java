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
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class TaskTopicServiceImpl extends ServiceImpl<TaskTopicMapper, TaskTopic> implements TaskTopicService {

    @Transactional
    @Override
    public String create(TaskTopicCreateParam param) {
        checkTimeRange(param.getStartTime(), param.getEndTime());
        PreconditionUtils.checkIsNull(getById(param.getTaskCode()), "任务编号已存在", HttpStatus.BAD_REQUEST);
        TaskTopic taskTopic = TaskTopic.builder()
                .taskCode(param.getTaskCode())
                .taskName(param.getTaskName())
                .taskType(param.getTaskType())
                .taskRegion(param.getTaskRegion())
                .startTime(param.getStartTime())
                .endTime(param.getEndTime())
                .description(param.getDescription())
                .ontologySpace(param.getOntologySpace())
                .status(param.getStatus())
                .creator(param.getCreator())
                .deleted(DeletedFlagEnum.NOT_DELETED.getCode())
                .build();
        save(taskTopic);
        return taskTopic.getTaskCode();
    }

    @Transactional
    @Override
    public void delete(String taskCode) {
        TaskTopic taskTopic = requireTask(taskCode);
        taskTopic.setDeleted(DeletedFlagEnum.DELETED.getCode());
        updateById(taskTopic);
    }

    @Override
    public TaskTopicDetailVO get(String taskCode) {
        return toDetailVO(requireTask(taskCode));
    }

    @Override
    public List<TaskTopicVO> list(TaskTopicQueryParam param) {
        List<TaskTopic> tasks = list(new LambdaQueryWrapper<TaskTopic>()
                .like(StringUtils.isNotBlank(param.getTaskName()), TaskTopic::getTaskName, param.getTaskName())
                .eq(StringUtils.isNotBlank(param.getTaskType()), TaskTopic::getTaskType, param.getTaskType())
                .eq(StringUtils.isNotBlank(param.getTaskRegion()), TaskTopic::getTaskRegion, param.getTaskRegion())
                .ge(param.getStartTime() != null, TaskTopic::getStartTime, param.getStartTime())
                .le(param.getEndTime() != null, TaskTopic::getEndTime, param.getEndTime())
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
        taskTopic.setStartTime(param.getStartTime());
        taskTopic.setEndTime(param.getEndTime());
        taskTopic.setDescription(param.getDescription());
        taskTopic.setOntologySpace(param.getOntologySpace());
        taskTopic.setStatus(param.getStatus());
        updateById(taskTopic);
    }

    private TaskTopic requireTask(String taskCode) {
        TaskTopic taskTopic = getOne(new LambdaQueryWrapper<TaskTopic>()
                .eq(TaskTopic::getTaskCode, taskCode)
                .eq(TaskTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkNotNull(taskTopic, "任务不存在", HttpStatus.BAD_REQUEST);
        return taskTopic;
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
