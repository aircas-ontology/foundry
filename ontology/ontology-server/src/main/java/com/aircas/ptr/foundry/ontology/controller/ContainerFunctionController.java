package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc;
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
 * 容器算子创建向导的后端辅助接口（镜像上传 / 接口文档上传 / 探查接口）。
 * <p>算子保存（生成）已并入统一创建入口 {@code POST /function}（{@code type=CONTAINER}），此处不再提供独立保存端点。</p>
 */
@Tag(name = "容器算子管理")
@RestController
@RequestMapping("/function/container")
public class ContainerFunctionController {

    @Resource
    private ContainerFunctionService containerFunctionService;

    @Operation(summary = "上传镜像（tar/tar.gz），校验镜像架构（container-operator.allowed-architectures）并返回镜像信息")
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
}
