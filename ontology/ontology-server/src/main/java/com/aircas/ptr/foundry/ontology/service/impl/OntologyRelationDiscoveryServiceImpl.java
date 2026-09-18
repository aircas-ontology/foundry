package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.dto.LlmRelationDiscoveryResponseDTO;
import com.aircas.ptr.foundry.ontology.model.dto.LlmRelationSuggestionDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyRelationDiscoveryParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyRelationDiscoveryResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyRelationSuggestionVO;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologyRelationDiscoveryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 本体关系发现服务实现
 * <p>
 * 流程：
 * 1) 校验 spaceId，查询同空间下已存在本体（status=1）
 * 2) 若候选为空，直接返回空列表，不调用 LLM
 * 3) 组装 prompt：新本体信息 + 候选清单 + 关系类型枚举 + 输出格式约束
 * 4) 调用 ChatClient 拿到 JSON 响应
 * 5) 解析、按 apiName / displayName 归一化 target，过滤幻觉项
 * 6) 拼装 VO 返回
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OntologyRelationDiscoveryServiceImpl implements OntologyRelationDiscoveryService {

    private static final Integer STATUS_VALID = 1;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 从 LLM 返回文本中兜底提取 JSON 对象 */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    /** 系统提示词：让模型严格按 JSON 返回，不产生额外文字 */
    private static final String SYSTEM_PROMPT = """
            你是本体工程专家。你的任务是判断【新本体】与【候选本体清单】中的哪些本体存在**语义关系**，
            并为每条关系指定类型和置信度。严格遵守以下规则：
            1. 只能选择候选清单中出现过的本体（用 apiName 引用），不要臆造。
            2. relationType 必须是以下枚举之一：
               - COMPOSITION   组合关系（部分-整体、装备、构成）
               - RECONNAISSANCE 侦察关系（探测、监视、感知）
               - STRIKE        打击关系（攻击、摧毁、压制）
               - COORDINATION  协同关系（配合、支援、通信）
               - OTHER         其他关系（不属于以上四类但确有语义关联）
            3. confidence 是 0~1 的小数，表示你对该关系的把握程度。
            4. reasoning 是 50 字以内的中文判断理由。
            5. 如果与所有候选本体都无关系，返回空数组：{"relations":[]}
            6. 输出必须是**单一 JSON 对象**，不要包裹 markdown 代码块，不要有任何解释性文字。
               结构固定为：{"relations":[{"target":"apiName","relationType":"枚举名","confidence":0.9,"reasoning":"..."}]}
            """;

    private final OntologyMetaService ontologyMetaService;
    private final ChatClient chatClient;

    @Override
    public OntologyRelationDiscoveryResultVO discover(OntologyRelationDiscoveryParam param) {
        // 1. 查同空间下已存在的本体
        List<OntologyMeta> existing = ontologyMetaService.list(
                new LambdaQueryWrapper<OntologyMeta>()
                        .eq(OntologyMeta::getOntologySpaceId, param.getSpaceId())
                        .eq(OntologyMeta::getStatus, STATUS_VALID));
        log.info("关系发现：spaceId={}, 新本体名称={}, 已存在本体数={}",
                param.getSpaceId(), param.getNewOntologyName(), existing.size());

        // 2. 空间下没有已存在本体 → 直接返回空结果，不消耗 LLM token
        if (CollectionUtils.isEmpty(existing)) {
            return OntologyRelationDiscoveryResultVO.builder()
                    .spaceId(param.getSpaceId())
                    .newOntologyName(param.getNewOntologyName())
                    .existingOntologyCount(0)
                    .relations(Collections.emptyList())
                    .build();
        }

        // 3. 建立 apiName/displayName -> OntologyMeta 的索引，用于后续归一化 LLM 输出
        Map<String, OntologyMeta> index = buildIndex(existing);

        // 4. 调 LLM
        LlmRelationDiscoveryResponseDTO llmResponse = callLlm(param, existing);

        // 5. 解析、归一化、过滤
        List<OntologyRelationSuggestionVO> suggestions = normalize(llmResponse, index);

        return OntologyRelationDiscoveryResultVO.builder()
                .spaceId(param.getSpaceId())
                .newOntologyName(param.getNewOntologyName())
                .existingOntologyCount(existing.size())
                .relations(suggestions)
                .build();
    }

    // ------------------------------------------------------------------
    // LLM 交互
    // ------------------------------------------------------------------

    private LlmRelationDiscoveryResponseDTO callLlm(OntologyRelationDiscoveryParam param,
                                                    List<OntologyMeta> existing) {
        String userPrompt = buildUserPrompt(param, existing);
        log.debug("关系发现 prompt：{}", userPrompt);

        String raw;
        try {
            raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("关系发现：调用大模型失败", e);
            throw new BusinessException("调用大模型失败: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        log.info("关系发现：大模型原始响应：{}", raw);
        return parseResponse(raw);
    }

    private String buildUserPrompt(OntologyRelationDiscoveryParam param, List<OntologyMeta> existing) {
        StringBuilder sb = new StringBuilder();
        sb.append("【新本体】\n")
                .append("名称: ").append(param.getNewOntologyName()).append('\n')
                .append("描述: ")
                .append(StringUtils.defaultIfBlank(param.getNewOntologyDescription(), "(未提供)"))
                .append("\n\n");

        sb.append("【候选本体清单】（格式：序号. apiName | 显示名 | 描述）\n");
        int idx = 1;
        for (OntologyMeta m : existing) {
            sb.append(idx++).append(". ")
                    .append(StringUtils.defaultIfBlank(m.getApiName(), "(无apiName)"))
                    .append(" | ")
                    .append(StringUtils.defaultIfBlank(m.getDisplayName(), "(无显示名)"))
                    .append(" | ")
                    .append(StringUtils.defaultIfBlank(m.getDescription(), "(无描述)"))
                    .append('\n');
        }

        sb.append("\n请判断新本体与候选清单中哪些本体存在关系，按规定 JSON 格式返回。")
                .append("\n提示：target 字段必须使用候选清单中的 apiName；若与所有候选都无关系，返回空数组。");
        return sb.toString();
    }

    private LlmRelationDiscoveryResponseDTO parseResponse(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new BusinessException("大模型返回为空", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String json = extractJsonBlock(raw);
        if (json == null) {
            throw new BusinessException("大模型返回格式非法，无法提取 JSON。raw=" + raw,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            LlmRelationDiscoveryResponseDTO dto =
                    OBJECT_MAPPER.readValue(json, LlmRelationDiscoveryResponseDTO.class);
            return dto == null ? new LlmRelationDiscoveryResponseDTO(Collections.emptyList()) : dto;
        } catch (Exception e) {
            log.error("关系发现：解析大模型响应失败，raw={}", raw, e);
            throw new BusinessException("大模型返回 JSON 解析失败: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String extractJsonBlock(String raw) {
        String trimmed = raw.trim();
        // 去掉可能的 markdown 代码块
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        Matcher m = JSON_OBJECT_PATTERN.matcher(trimmed);
        return m.find() ? m.group() : null;
    }

    // ------------------------------------------------------------------
    // 归一化 + 组装
    // ------------------------------------------------------------------

    /**
     * 建立多种键到 OntologyMeta 的索引，容忍 LLM 用 apiName / displayName / uniqueIdentifier 引用。
     * key 统一转小写以支持大小写不敏感匹配。
     */
    private Map<String, OntologyMeta> buildIndex(List<OntologyMeta> existing) {
        Map<String, OntologyMeta> index = new HashMap<>();
        for (OntologyMeta m : existing) {
            if (StringUtils.isNotBlank(m.getApiName())) {
                index.putIfAbsent(m.getApiName().toLowerCase(), m);
            }
            if (StringUtils.isNotBlank(m.getDisplayName())) {
                index.putIfAbsent(m.getDisplayName().toLowerCase(), m);
            }
            if (StringUtils.isNotBlank(m.getUniqueIdentifier())) {
                index.putIfAbsent(m.getUniqueIdentifier().toLowerCase(), m);
            }
        }
        return index;
    }

    private List<OntologyRelationSuggestionVO> normalize(LlmRelationDiscoveryResponseDTO response,
                                                         Map<String, OntologyMeta> index) {
        if (response == null || CollectionUtils.isEmpty(response.getRelations())) {
            return Collections.emptyList();
        }
        List<OntologyRelationSuggestionVO> result = new ArrayList<>();
        // 同一个 target 可能被 LLM 返回多次，用 seen 去重
        Map<String, Boolean> seen = new HashMap<>();

        for (LlmRelationSuggestionDTO item : response.getRelations()) {
            if (item == null || StringUtils.isBlank(item.getTarget())) {
                continue;
            }
            OntologyMeta target = index.get(item.getTarget().trim().toLowerCase());
            if (target == null) {
                // 幻觉：LLM 返回了不在候选清单里的 target，直接丢弃并记录日志
                log.warn("关系发现：丢弃幻觉目标={}", item.getTarget());
                continue;
            }
            String dedupKey = target.getUniqueIdentifier();
            if (Boolean.TRUE.equals(seen.putIfAbsent(dedupKey, Boolean.TRUE))) {
                continue;
            }

            OntologyLinkTypeEnum type = parseLinkType(item.getRelationType());
            result.add(OntologyRelationSuggestionVO.builder()
                    .targetUniqueIdentifier(target.getUniqueIdentifier())
                    .targetDisplayName(target.getDisplayName())
                    .targetApiName(target.getApiName())
                    .targetDescription(target.getDescription())
                    .relationType(type)
                    .relationTypeName(type.getName())
                    .confidence(clampConfidence(item.getConfidence()))
                    .reasoning(item.getReasoning())
                    .build());
        }
        // 置信度降序，前端优先展示高置信度关系
        return result.stream()
                .sorted((a, b) -> Double.compare(
                        b.getConfidence() == null ? 0 : b.getConfidence(),
                        a.getConfidence() == null ? 0 : a.getConfidence()))
                .collect(Collectors.toList());
    }

    /**
     * 解析关系类型；无效或缺失时归为 OTHER，避免因 LLM 输出不规范导致整个接口失败
     */
    private OntologyLinkTypeEnum parseLinkType(String raw) {
        if (StringUtils.isBlank(raw)) {
            return OntologyLinkTypeEnum.OTHER;
        }
        return Arrays.stream(OntologyLinkTypeEnum.values())
                .filter(e -> e.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("关系发现：未知关系类型={}，已回退为 OTHER", raw);
                    return OntologyLinkTypeEnum.OTHER;
                });
    }

    /**
     * 置信度合法化：null → 0.5（中性）；越界 → 截断到 [0, 1]
     */
    private Double clampConfidence(Double raw) {
        if (raw == null || raw.isNaN()) {
            return 0.5;
        }
        if (raw < 0) return 0.0;
        if (raw > 1) return 1.0;
        return raw;
    }
}
