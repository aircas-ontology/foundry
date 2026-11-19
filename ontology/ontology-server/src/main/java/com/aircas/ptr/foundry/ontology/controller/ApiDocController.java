package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.util.DownloadUtil;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.service.ApiDocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;


@Tag(name = "接口文档")
@RestController
@Validated
@RequestMapping("/api_doc")
public class ApiDocController {

    @Resource
    private ApiDocService apiDocService;

    @GetMapping("/export")
    @Operation(summary = "导出接口文档（HTML 文件），包含查询对象、查询属性、查询关系、查询实例")
    public void exportApiDoc(@RequestParam(name = "spaceId", required = true)
                             @Parameter(description = "对象空间id，导出后写入文档示例参数")
                             @SpaceIdVerify Integer spaceId,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        var html = apiDocService.renderApiDocHtml(spaceId, resolveBaseUrl(request));
        DownloadUtil.writeHtmlAttachment(response, "api-doc-space-" + spaceId + ".html", html);
    }

    /**
     * 服务基址取自本次请求，导出的文档换环境后仍然可点。
     */
    private String resolveBaseUrl(HttpServletRequest request) {
        var scheme = request.getScheme();
        var port = request.getServerPort();
        var defaultPort = port <= 0
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port) + request.getContextPath();
    }
}
