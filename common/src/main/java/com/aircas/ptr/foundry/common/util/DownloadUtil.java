package com.aircas.ptr.foundry.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 文件下载响应工具：统一设置下载响应头并写出内容，避免各 Controller 重复下载样板代码。
 */
public final class DownloadUtil {

    private DownloadUtil() {
    }

    /**
     * 以附件（attachment）形式写出 JSON 内容。
     *
     * <p>文件名按 RFC 5987 编码（{@code filename*=UTF-8''...}）并保留 ASCII 回退，兼容非 ASCII 文件名；
     * 序列化直接复用传入的 {@link ObjectMapper}（其 non_null 等策略由全局 spring.jackson 配置决定），不额外改写。</p>
     *
     * @param response     HTTP 响应
     * @param objectMapper Jackson 序列化器（通常为 Spring 托管 Bean）
     * @param fileName     下载文件名
     * @param data         待序列化的对象
     * @throws IOException 写出响应流失败时抛出
     */
    public static void writeJsonAttachment(HttpServletResponse response, ObjectMapper objectMapper,
                                           String fileName, Object data) throws IOException {
        var contentType = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);
        var contentDisposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        response.setContentType(contentType.toString());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
        objectMapper.writeValue(response.getOutputStream(), data);
        response.getOutputStream().flush();
    }
}
