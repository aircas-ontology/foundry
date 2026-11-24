package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.topic.model.enums.DeletedFlagEnum;
import com.aircas.ptr.foundry.topic.model.param.EventStageCreateParam;
import com.aircas.ptr.foundry.topic.model.po.EventStage;
import com.aircas.ptr.foundry.topic.model.po.TaskTopic;
import com.aircas.ptr.foundry.topic.repository.EventStageMapper;
import com.aircas.ptr.foundry.topic.repository.TaskTopicMapper;
import com.aircas.ptr.foundry.topic.service.EventStageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Date;

@Service
public class EventStageServiceImpl extends ServiceImpl<EventStageMapper, EventStage> implements EventStageService {

    @Resource
    private TaskTopicMapper taskTopicMapper;

    @Transactional
    @Override
    public String create(EventStageCreateParam param) {
        checkTimeRange(param.getStartTime(), param.getEndTime());
        TaskTopic taskTopic = taskTopicMapper.selectOne(new LambdaQueryWrapper<TaskTopic>()
                .eq(TaskTopic::getTaskCode, param.getTaskCode())
                .eq(TaskTopic::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkNotNull(taskTopic, "任务不存在", HttpStatus.BAD_REQUEST);
        String stageCode = "S" + SnowflakeIdUtil.get();
        EventStage stage = EventStage.builder()
                .stageCode(stageCode)
                .taskCode(param.getTaskCode())
                .stageName(param.getStageName())
                .startTime(param.getStartTime())
                .endTime(param.getEndTime())
                .eventCodes(param.getEventCodes())
                .creator(StringUtils.isBlank(param.getCreator()) ? "admin" : param.getCreator().trim())
                .deleted(DeletedFlagEnum.NOT_DELETED.getCode())
                .build();
        save(stage);
        return stageCode;
    }

    private void checkTimeRange(Date startTime, Date endTime) {
        PreconditionUtils.checkArgument(!endTime.before(startTime), "事件阶段结束时间不能早于开始时间", HttpStatus.BAD_REQUEST);
    }
}
