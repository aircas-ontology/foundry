package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.UploadFileCreateParam;
import com.aircas.ptr.foundry.topic.model.param.UploadFileQueryParam;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileVO;
import com.aircas.ptr.foundry.topic.service.UploadFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "上传文件")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/upload-file")
public class UploadFileController {

    private final UploadFileService uploadFileService;

    @PostMapping
    @Operation(summary = "创建上传文件")
    public RestResult<Long> create(@RequestBody @Valid UploadFileCreateParam param) {
        return RestResult.ofData(uploadFileService.create(param));
    }

    @GetMapping
    @Operation(summary = "按关键词查询未删除的上传文件")
    public RestResult<List<UploadFileVO>> list(@Valid UploadFileQueryParam param) {
        return RestResult.ofData(uploadFileService.listFiles(param));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除上传文件")
    public RestResult<Void> delete(@PathVariable Long id) {
        uploadFileService.delete(id);
        return RestResult.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "按文件编号查询上传文件详情")
    public RestResult<UploadFileDetailVO> get(@PathVariable Long id) {
        return RestResult.ofData(uploadFileService.get(id));
    }
}
