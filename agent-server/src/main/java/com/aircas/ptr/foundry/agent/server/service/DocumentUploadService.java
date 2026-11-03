package com.aircas.ptr.foundry.agent.server.service;

import com.aircas.ptr.foundry.agent.server.model.vo.DocumentUploadVO;
import com.aircas.ptr.foundry.agent.server.session.SessionStateStore;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 会话参考文档上传服务：对象构建第 2 步支持用户在选数据源之外上传一份参考文档（当前仅 txt/md），
 * 文档中可能包含对象描述、属性信息等。本服务把文件解析为纯文本（UTF-8 优先、GBK 兜底，超长截断），
 * 以 {@link SessionStateStore#STAGE_DOCUMENT_UPLOADED} 状态块按 sessionId 持久化，
 * 后续每轮由 {@code AgentChatService} 注入 system 上下文，使第 1/3/4 步的产出结合文档内容。
 *
 * <p>不做对象存储留存、不持久化到库：文档只服务于当前会话的对象构建流程，
 * 随会话状态过期（Caffeine TTL）或清空上下文一并消失。重复上传同一会话以最新一份覆盖。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadService {

    /** 允许的扩展名：当前仅纯文本/markdown（docx/pdf 需引入解析依赖后再扩展）。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "md", "markdown");

    /** 注入上下文的正文最大字符数，超出截断，避免长文档挤占 system 提示词。 */
    private static final int MAX_CONTENT_CHARS = 8000;

    /** Windows 记事本 txt 常见 GBK 编码，UTF-8 严格解码失败时的兜底。 */
    private static final Charset GBK = Charset.forName("GBK");

    private final SessionStateStore sessionStateStore;

    /** 复用 Spring Boot 自动装配的统一 ObjectMapper Bean，保证序列化配置一致。 */
    private final ObjectMapper objectMapper;

    /**
     * 解析并登记会话参考文档。
     *
     * @param file      上传文件（txt/md，非空）
     * @param sessionId 会话 id，文档状态挂在该会话上
     * @return 上传结果（文件名 / 保留字符数 / 是否截断）
     */
    public DocumentUploadVO upload(MultipartFile file, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException("会话 id 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文档内容不能为空", HttpStatus.BAD_REQUEST);
        }
        String fileName = baseFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    "当前仅支持 txt/md 格式的参考文档：" + fileName, HttpStatus.BAD_REQUEST);
        }

        String text = stripBom(decode(file));
        if (text.isBlank()) {
            throw new BusinessException("文档解析后内容为空：" + fileName,
                    HttpStatus.BAD_REQUEST);
        }
        boolean truncated = text.length() > MAX_CONTENT_CHARS;
        if (truncated) {
            text = text.substring(0, MAX_CONTENT_CHARS);
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("stage", SessionStateStore.STAGE_DOCUMENT_UPLOADED);
        node.put("fileName", fileName);
        node.put("charCount", text.length());
        node.put("truncated", truncated);
        node.put("content", text);
        sessionStateStore.record(sessionId, SessionStateStore.STAGE_DOCUMENT_UPLOADED, node.toString());
        log.info("[DocumentUpload] 参考文档已登记 sessionId={}, fileName={}, chars={}, truncated={}",
                sessionId, fileName, text.length(), truncated);

        return DocumentUploadVO.builder()
                .fileName(fileName)
                .charCount(text.length())
                .truncated(truncated)
                .build();
    }

    /**
     * 字节内容解码为文本：先按 UTF-8 严格解码（非法字节即失败），失败则按 GBK 宽松解码，
     * 覆盖记事本另存 UTF-8/ANSI 两种常见情况。
     */
    private String decode(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败", HttpStatus.BAD_REQUEST);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (Exception e) {
            return new String(bytes, GBK);
        }
    }

    private String stripBom(String text) {
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    /** 只取文件名部分，丢弃客户端可能携带的路径信息。 */
    private String baseFileName(String original) {
        if (original == null || original.isBlank()) {
            return "document.txt";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
