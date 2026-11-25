package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.EventTopicOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.vo.EventTopicOverviewVO;
import com.aircas.ptr.foundry.topic.service.EventTopicOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @Operation(summary = "按事件主题编号查询总览统计")
    public RestResult<List<EventTopicOverviewVO>> list(@Parameter(description = "事件主题编号", example = "1")
                                                       @RequestParam
                                                       @NotNull(message = "事件主题编号不能为空")
                                                       Long eventTopicId) {
        return RestResult.ofData(eventTopicOverviewService.listByEventTopicId(eventTopicId));
    }
}
