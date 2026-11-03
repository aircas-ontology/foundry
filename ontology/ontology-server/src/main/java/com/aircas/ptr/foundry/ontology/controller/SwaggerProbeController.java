package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.config.ContainerOperatorProperties;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc;
import com.aircas.ptr.foundry.ontology.model.param.ContainerProbeParam;
import com.aircas.ptr.foundry.ontology.model.param.ExternalApiConfig;
import com.aircas.ptr.foundry.ontology.model.param.SwaggerConvertParam;
import com.aircas.ptr.foundry.ontology.service.OpenApiProbeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Swagger/OpenAPI 文档导入：上传文档 → 探查接口 → 转换为外部API算子配置。
 * <p>与容器算子共用同一套 OpenAPI 解析能力（{@link OpenApiProbeService}），
 * 但面向外部API算子（WebAPI）场景：用户导入 Swagger 文档后选取接口，
 * 一键生成 {@link ExternalApiConfig} 的出入参定义，免去手工逐个添加端口。</p>
 */
@Slf4j
@Tag(name = "Swagger接口导入")
@RestController
@RequestMapping("/function/swagger")
public class SwaggerProbeController {

    @Resource
    private OpenApiProbeService openApiProbeService;

    @Resource
    private ContainerOperatorProperties properties;

    @Operation(summary = "上传 Swagger/OpenAPI 文档（json/yaml/yml），返回文档信息与存储路径")
    @PostMapping("/upload_doc")
    public RestResult<ContainerInterfaceDoc> uploadDoc(@RequestParam("file") MultipartFile file) {
        String fileName = sanitizeFileName(file);
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".json") && !lower.endsWith(".yaml") && !lower.endsWith(".yml")) {
            throw new BusinessException("接口文档仅支持 .json / .yaml / .yml 格式", HttpStatus.BAD_REQUEST);
        }
        Path dir = properties.resolveDocDir();
        Path target = dir.resolve(UUID.randomUUID() + "-" + fileName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("接口文档保存失败：" + e.getMessage());
        }
        ContainerInterfaceDoc docInfo = ContainerInterfaceDoc.builder()
                .fileName(fileName)
                .path(target.toAbsolutePath().toString())
                .format(openApiProbeService.detectFormat(target.toAbsolutePath().toString()))
                .build();
        return RestResult.ofData(docInfo);
    }

    @Operation(summary = "探查接口文档，解析出接口列表（名称/方法/路径/出入参）")
    @PostMapping("/probe_interfaces")
    public RestResult<List<ContainerInterface>> probeInterfaces(@RequestBody @Valid ContainerProbeParam param) {
        return RestResult.ofData(openApiProbeService.probe(param.getDocPath()));
    }

    @Operation(summary = "将探查到的接口转换为外部API算子配置（ExternalApiConfig），方便一键填充出入参")
    @PostMapping("/to_external_api")
    public RestResult<ExternalApiConfig> toExternalApi(@RequestBody @Valid SwaggerConvertParam param) {
        return RestResult.ofData(openApiProbeService.toExternalApiConfig(param.getInterfaceSpec(), param.getBaseUrl()));
    }

    private String sanitizeFileName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return "swagger-doc";
        }
        return original.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
