package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.EventTopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.EventTopicQueryParam;
import com.aircas.ptr.foundry.topic.model.param.EventTopicUpdateParam;
import com.aircas.ptr.foundry.topic.model.vo.EventTopicVO;
import com.aircas.ptr.foundry.topic.service.EventTopicService;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

@Tag(name = "事件主题")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/event-topic")
public class EventTopicController {

    private final EventTopicService eventTopicService;

    @PostMapping
    @Operation(summary = "创建事件主题")
    public RestResult<Long> create(@RequestBody @Valid EventTopicCreateParam param) {
        return RestResult.ofData(eventTopicService.create(param));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除事件主题")
    public RestResult<Void> delete(@Parameter(description = "事件主题编号，多个用逗号分割")
                                   @PathVariable String id) {
        eventTopicService.delete(id);
        return RestResult.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "按事件主题编号查询详情")
    public RestResult<EventTopicVO> get(@PathVariable String id) {
        return RestResult.ofData(eventTopicService.get(id));
    }

    @GetMapping
    @Operation(summary = "分页查询未删除的事件主题")
    public RestResult<IPage<EventTopicVO>> page(@Valid EventTopicQueryParam param) {
        return RestResult.ofData(eventTopicService.page(param));
    }

    @PutMapping
    @Operation(summary = "按事件主题编号编辑事件主题")
    public RestResult<Void> update(@RequestBody @Valid EventTopicUpdateParam param) {
        eventTopicService.update(param);
        return RestResult.success();
    }
}
