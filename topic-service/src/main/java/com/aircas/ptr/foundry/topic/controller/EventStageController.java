package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.EventStageCreateParam;
import com.aircas.ptr.foundry.topic.service.EventStageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "事件阶段")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/event-stage")
public class EventStageController {

    private final EventStageService eventStageService;

    @PostMapping
    @Operation(summary = "创建事件阶段")
    public RestResult<String> create(@RequestBody @Valid EventStageCreateParam param) {
        return RestResult.ofData(eventStageService.create(param));
    }
}
