package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.service.ApiDocService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class ApiDocServiceImpl implements ApiDocService {

    private static final String TEMPLATE_PATH = "templates/api-doc.html";
    private static final String SPACE_ID_TOKEN = "{{spaceId}}";
    private static final String BASE_URL_TOKEN = "{{baseUrl}}";

    @Override
    public String renderApiDocHtml(Integer spaceId, String baseUrl) {
        return loadTemplate()
                .replace(SPACE_ID_TOKEN, String.valueOf(spaceId))
                .replace(BASE_URL_TOKEN, baseUrl);
    }

    /**
     * 打成 jar 后模板在classpath里，用 File 读不到，必须走流。
     */
    private String loadTemplate() {
        try (var in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("接口文档模板读取失败：" + TEMPLATE_PATH, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
