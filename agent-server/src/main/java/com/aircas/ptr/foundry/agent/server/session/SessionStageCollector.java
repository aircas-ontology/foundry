package com.aircas.ptr.foundry.agent.server.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从大模型回复文本中抽取带 {@code stage} 字段的 JSON 块，并写入 {@link SessionStateStore}。
 *
 * <p>本体构建各阶段的结构化输出（对象定义、属性选择、关系选择等）都带有 {@code stage} 标记。
 * 每轮回复结束后调用 {@link #collectAndStore}，把这些块解析出来持久化，使状态跨轮不丢，
 * 不受 ChatMemory 滑动窗口淘汰影响。</p>
 *
 * <p>抽取采用大括号配平扫描（感知字符串字面量与转义），兼容模型用 ```json 围栏包裹或裸输出 JSON 两种情况，
 * 且能处理一条回复里包含多个 JSON 块的情形。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionStageCollector {

    private final SessionStateStore sessionStateStore;

    /** 复用 Spring Boot 自动装配的统一 ObjectMapper Bean（而非各处 new），保证序列化配置一致。 */
    private final ObjectMapper objectMapper;

    /**
     * 解析回复文本，将其中所有带 stage 的 JSON 块记录到会话状态存储。
     *
     * @param sessionId 会话 id
     * @param replyText 大模型本轮完整回复文本
     */
    public void collectAndStore(String sessionId, String replyText) {
        if (sessionId == null || replyText == null || replyText.isBlank()) {
            return;
        }
        for (String json : extractJsonObjects(replyText)) {
            try {
                JsonNode node = objectMapper.readTree(json);
                JsonNode stageNode = node.get("stage");
                if (stageNode != null && stageNode.isTextual()) {
                    sessionStateStore.record(sessionId, stageNode.asText(), json);
                }
            } catch (Exception e) {
                // 非合法 JSON 或解析异常：跳过该块，不影响其他块
                log.debug("[StageCollector] 跳过无法解析的 JSON 块 sessionId={}", sessionId);
            }
        }
    }

    /**
     * 用大括号配平从文本中抽取所有顶层 JSON 对象子串。
     *
     * <p>扫描到 {@code {} 时开始配平计数，遇字符串字面量（双引号包裹）内部的括号不计数，
     * 正确处理 {@code \} 转义；配平归零即截取一个完整对象。仅返回顶层对象，嵌套对象随外层一并返回。</p>
     *
     * @param text 待扫描文本
     * @return 抽取到的 JSON 对象子串列表（可能为空）
     */
    static List<String> extractJsonObjects(String text) {
        List<String> result = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            if (text.charAt(i) != '{') {
                i++;
                continue;
            }
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            int start = i;
            boolean completed = false;
            for (int j = i; j < n; j++) {
                char ch = text.charAt(j);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == '"') {
                        inString = false;
                    }
                } else {
                    if (ch == '"') {
                        inString = true;
                    } else if (ch == '{') {
                        depth++;
                    } else if (ch == '}') {
                        depth--;
                        if (depth == 0) {
                            result.add(text.substring(start, j + 1));
                            i = j + 1;
                            completed = true;
                            break;
                        }
                    }
                }
            }
            // 未配平（畸形输入）：跳过起始 '{'，避免死循环
            if (!completed) {
                i = start + 1;
            }
        }
        return result;
    }
}
