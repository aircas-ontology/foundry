package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc;
import com.aircas.ptr.foundry.ontology.model.param.ContainerFunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ContainerProbeParam;
import com.aircas.ptr.foundry.ontology.service.ContainerFunctionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 容器算子注册：五步创建向导的后端接口（镜像上传 / 接口文档上传 / 探查接口 / 保存算子）。
 */
@Tag(name = "容器算子管理")
@RestController
@RequestMapping("/function/container")
public class ContainerFunctionController {

    @Resource
    private ContainerFunctionService containerFunctionService;

    @Operation(summary = "上传镜像（tar/tar.gz），校验 ARM64 架构并返回镜像信息")
    @PostMapping("/upload_image")
    public RestResult<ContainerImageInfo> uploadImage(@RequestParam("file") MultipartFile file) {
        return RestResult.ofData(containerFunctionService.uploadImage(file));
    }

    @Operation(summary = "上传接口文档（json/yaml/yml），返回文档信息与存储路径")
    @PostMapping("/upload_doc")
    public RestResult<ContainerInterfaceDoc> uploadDoc(@RequestParam("file") MultipartFile file) {
        return RestResult.ofData(containerFunctionService.uploadDoc(file));
    }

    @Operation(summary = "探查接口文档，解析出接口列表（名称/api/请求方式/路径/描述/出入参）")
    @PostMapping("/probe_interfaces")
    public RestResult<List<ContainerInterface>> probeInterfaces(@RequestBody @Valid ContainerProbeParam param) {
        return RestResult.ofData(containerFunctionService.probeInterfaces(param.getDocPath()));
    }

    @Operation(summary = "保存容器算子（每个勾选接口生成一条独立算子，返回生成的 api 列表）")
    @PostMapping
    public RestResult<List<String>> createContainerFunction(@RequestBody @Valid ContainerFunctionCreateParam param) {
        return RestResult.ofData(containerFunctionService.createContainerFunction(param));
    }
}
