package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.service.FileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;


@Api(tags = "文件处理")
@RequestMapping("/file")
@RestController
public class FileController {


    @Resource
    private FileService fileService;


    @ApiOperation(value = "上传图片文件,获取缩略图url")
    @PostMapping("/thumbnail")
    public RestResult<String> getThumbnailByImage(@RequestParam(required = true, name = "image") MultipartFile image) throws Exception {
        return RestResult.ofData(fileService.getThumbnailByImage(image));
    }

    @ApiOperation(value = "上传图片文件,获取url")
    @PostMapping("/url")
    public RestResult<String> getUrlByImage(@RequestParam(required = true, name = "image") MultipartFile image) throws Exception {
        return RestResult.ofData(fileService.getUrlByImage(image));
    }

    @ApiOperation(value = "上传文件,获取预览链接")
    @PostMapping("/previewUrl")
    public RestResult<String> getPreviewUrl(@RequestParam(required = true, name = "file") MultipartFile file) throws Exception {
        return RestResult.ofData(fileService.getPreviewUrl(file));
    }
}
