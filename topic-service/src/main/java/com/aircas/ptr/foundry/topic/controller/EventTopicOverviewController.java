package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.EventTopicOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.vo.EventTopicOverviewStatVO;
import com.aircas.ptr.foundry.topic.service.EventTopicOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "事件主题总览统计")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/event-topic-overview")
public class EventTopicOverviewController {

    private final EventTopicOverviewService eventTopicOverviewService;

    @PostMapping
    @Operation(summary = "创建事件主题总览统计")
    public RestResult<Long> create(@RequestBody @Valid EventTopicOverviewCreateParam param) {
        return RestResult.ofData(eventTopicOverviewService.create(param));
    }

    @GetMapping
    @Operation(summary = "查询事件主题总览统计，相同类型的数量和占比分别求和")
    public RestResult<List<EventTopicOverviewStatVO>> list() {
        return RestResult.ofData(eventTopicOverviewService.listStats());
    }
}
