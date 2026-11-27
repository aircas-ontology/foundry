package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationOverviewUpdateParam;
import com.aircas.ptr.foundry.topic.service.ImplicitRelationOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "隐含关系总览统计")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/implicit-relation-overview")
public class ImplicitRelationOverviewController {

    private final ImplicitRelationOverviewService implicitRelationOverviewService;

    @PostMapping
    @Operation(summary = "创建隐含关系总览统计")
    public RestResult<Long> create(@RequestBody @Valid ImplicitRelationOverviewCreateParam param) {
        return RestResult.ofData(implicitRelationOverviewService.create(param));
    }

    @PutMapping
    @Operation(summary = "更新隐含关系总览统计")
    public RestResult<Void> update(@RequestBody @Valid ImplicitRelationOverviewUpdateParam param) {
        implicitRelationOverviewService.update(param);
        return RestResult.success();
    }
}
