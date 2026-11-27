package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.UploadFileOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.param.UploadFileOverviewUpdateParam;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileOverviewStatVO;
import com.aircas.ptr.foundry.topic.service.UploadFileOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "文件总览统计")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/upload-file-overview")
public class UploadFileOverviewController {

    private final UploadFileOverviewService uploadFileOverviewService;

    @PostMapping
    @Operation(summary = "创建文件总览统计")
    public RestResult<Long> create(@RequestBody @Valid UploadFileOverviewCreateParam param) {
        return RestResult.ofData(uploadFileOverviewService.create(param));
    }

    @PutMapping
    @Operation(summary = "修改文件总览统计")
    public RestResult<Void> update(@RequestBody @Valid UploadFileOverviewUpdateParam param) {
        uploadFileOverviewService.update(param);
        return RestResult.success();
    }

    @GetMapping
    @Operation(summary = "查询文件总览统计")
    public RestResult<List<UploadFileOverviewStatVO>> list() {
        return RestResult.ofData(uploadFileOverviewService.listStats());
    }
}
