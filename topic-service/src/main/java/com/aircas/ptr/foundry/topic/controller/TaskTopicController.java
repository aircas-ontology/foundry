package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicQueryParam;
import com.aircas.ptr.foundry.topic.model.param.TaskTopicUpdateParam;
import com.aircas.ptr.foundry.topic.model.vo.TaskTopicDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.TaskTopicVO;
import com.aircas.ptr.foundry.topic.service.TaskTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "任务主题")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/task-topic")
public class TaskTopicController {

    private final TaskTopicService taskTopicService;

    @PostMapping
    @Operation(summary = "创建任务主题")
    public RestResult<Long> create(@RequestBody @Valid TaskTopicCreateParam param) {
        return RestResult.ofData(taskTopicService.create(param));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除任务主题")
    public RestResult<Void> delete(@Parameter(description = "任务编号，多个用逗号分割") @PathVariable String id) {
        taskTopicService.delete(id);
        return RestResult.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "按任务编号查询任务主题详情")
    public RestResult<TaskTopicDetailVO> get(@PathVariable String id) {
        return RestResult.ofData(taskTopicService.get(id));
    }

    @GetMapping
    @Operation(summary = "按条件查询任务主题列表")
    public RestResult<List<TaskTopicVO>> list(@Valid TaskTopicQueryParam param) {
        return RestResult.ofData(taskTopicService.list(param));
    }

    @PutMapping
    @Operation(summary = "按任务编号编辑任务主题")
    public RestResult<Void> update(@RequestBody @Valid TaskTopicUpdateParam param) {
        taskTopicService.update(param);
        return RestResult.success();
    }
}
