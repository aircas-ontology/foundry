package com.aircas.ptr.foundry.agent.server.stream;

import com.aircas.ptr.foundry.agent.server.model.vo.AgentChatStreamVO;
import com.aircas.ptr.foundry.agent.server.model.vo.SelectionOptionVO;
import com.aircas.ptr.foundry.agent.server.session.SessionStageCollector;
import com.aircas.ptr.foundry.agent.server.session.SessionStateStore;
import com.aircas.ptr.foundry.agent.server.tool.McpToolInvoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性选择卡片解析器。
 *
 * <p><b>为什么完全后端驱动</b>：实测 {@code qwen-plus}（乃至 DeepSeek）在需要用户选择时，既不肯调用查询工具，
 * 也不肯输出 {@code request_selection} 文本标记，只会念一句"请在上方卡片中选择"——任何"依赖模型显式触发"的
 * 方案都会概率性失效。因此本类改为<b>纯状态机</b>：只依据模型<b>稳定产出</b>的信号决定推卡，模型无需做任何
 * function-calling 或特殊标记。</p>
 *
 * <p>可依赖的稳定信号有二：
 * <ol>
 *   <li><b>步骤横幅</b>：提示词工作原则#4 要求每步开头输出 {@code 【第N步/共11步】}，模型对此遵从度极高。
 *       第0步→space_select、第2步→datasource_select。</li>
 *   <li><b>已持久化的 proposed 状态块</b>：第4/5步模型会稳定输出 {@code properties_proposed} /
 *       {@code relations_proposed} 的 JSON（纯文本产出），本类据此推 properties_select / relations_select。</li>
 * </ol>
 * 另兼容模型主动输出的 {@code request_selection} 标记（若有则一并纳入候选）。</p>
 *
 * <p>取数来源：space/datasource 经 {@link McpToolInvoker} 程序化调 MCP 工具；properties/relations 从
 * {@link SessionStateStore} 回查。候选为空或取数失败时<b>不推卡</b>，交模型引导语说明。
 * 每轮按流程顺序<b>至多推一张</b>未满足的卡片，避免重复。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SelectionResolver {

    /** 步骤横幅匹配：【第N步/共11步】。 */
    private static final Pattern STEP_BANNER = Pattern.compile("【第\\s*(\\d+)\\s*步");

    /** proposed 状态块输出匹配：仅当本轮真的输出了 {"stage":"xxx_proposed"} JSON 块时才触发推卡。 */
    private static final Pattern PROPERTIES_PROPOSED = Pattern.compile("\"stage\"\\s*:\\s*\"properties_proposed\"");
    private static final Pattern RELATIONS_PROPOSED = Pattern.compile("\"stage\"\\s*:\\s*\"relations_proposed\"");

    /** scenario → 该场景"已完成"对应的 selected stage（存在即视为已满足，不再重复推卡）。 */
    private static final String SCENE_SPACE = "space_select";
    private static final String SCENE_DATASOURCE = "datasource_select";
    private static final String SCENE_PROPERTIES = "properties_select";
    private static final String SCENE_RELATIONS = "relations_select";

    private final McpToolInvoker mcpToolInvoker;
    private final SelectionCardFactory selectionCardFactory;
    private final SessionStateStore sessionStateStore;
    private final SessionStageCollector sessionStageCollector;

    /**
     * 一轮对话结束后，依据状态机决定是否推卡（确定性，不依赖模型显式触发）。
     *
     * @param sessionId 会话 id
     * @param replyText 本轮模型完整回复文本（用于解析步骤横幅 / proposed / 兼容标记）
     * @param sink      本次请求的事件 sink
     */
    public void resolveForRound(String sessionId, String replyText, Sinks.Many<AgentChatStreamVO> sink) {
        // 候选场景（有序去重）：状态机推断优先，其次模型主动标记
        Set<String> candidates = new LinkedHashSet<>();
        String inferred = inferScenario(sessionId, replyText);
        if (inferred != null) {
            candidates.add(inferred);
        }
        candidates.addAll(sessionStageCollector.extractSelectionScenarios(replyText));

        for (String scenario : candidates) {
            if (!SelectionCardFactory.supports(scenario)) {
                continue;
            }
            if (isSatisfied(sessionId, scenario)) {
                log.debug("[SelectionResolver] {} 已满足（selected 已存在），跳过推卡", scenario);
                continue;
            }
            resolveAndEmit(sessionId, scenario, sink);
            // 流程严格串行，一轮至多推一张卡；推成功即结束，避免同轮多卡
            return;
        }
    }

    /**
     * 状态机推断本轮应推的场景：
     * <ul>
     *   <li>本轮回复含 properties_proposed 且未选属性 → properties_select</li>
     *   <li>本轮回复含 relations_proposed 且未选关系 → relations_select</li>
     *   <li>横幅第0步且未选空间 → space_select</li>
     *   <li>横幅第2步且未选数据源 → datasource_select</li>
     * </ul>
     *
     * @return 推断出的 scenario；无可推断时返回 {@code null}
     */
    private String inferScenario(String sessionId, String replyText) {
        if (replyText == null || replyText.isBlank()) {
            return null;
        }
        // 第4/5步：模型本轮真的输出了 proposed JSON 状态块（而非仅提及）
        if (PROPERTIES_PROPOSED.matcher(replyText).find() && sessionStateStore.getStage(sessionId, "properties_selected") == null) {
            return SCENE_PROPERTIES;
        }
        if (RELATIONS_PROPOSED.matcher(replyText).find() && sessionStateStore.getStage(sessionId, "relations_selected") == null) {
            return SCENE_RELATIONS;
        }
        // 第0/2步：靠步骤横幅定位（模型对横幅遵从度高）
        Integer step = parseStep(replyText);
        if (step != null) {
            if (step == 0 && sessionStateStore.getStage(sessionId, "space_selected") == null) {
                return SCENE_SPACE;
            }
            if (step == 2 && sessionStateStore.getStage(sessionId, "datasource_selected") == null) {
                return SCENE_DATASOURCE;
            }
        }
        return null;
    }

    /** 解析回复中出现的第一个步骤横幅编号；无横幅返回 {@code null}。 */
    private Integer parseStep(String replyText) {
        Matcher m = STEP_BANNER.matcher(replyText);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 该场景是否已完成（对应 selected 状态块已存在），已完成则不再重复推卡。 */
    private boolean isSatisfied(String sessionId, String scenario) {
        switch (scenario) {
            case SCENE_SPACE:
                return sessionStateStore.getStage(sessionId, "space_selected") != null;
            case SCENE_DATASOURCE:
                return sessionStateStore.getStage(sessionId, "datasource_selected") != null;
            case SCENE_PROPERTIES:
                return sessionStateStore.getStage(sessionId, "properties_selected") != null;
            case SCENE_RELATIONS:
                return sessionStateStore.getStage(sessionId, "relations_selected") != null;
            default:
                return false;
        }
    }

    /**
     * 按 scenario 确定性取数并推 {@code selection_request}。
     */
    private void resolveAndEmit(String sessionId, String scenario, Sinks.Many<AgentChatStreamVO> sink) {
        List<SelectionOptionVO> options;
        try {
            options = buildOptions(sessionId, scenario);
        } catch (Exception e) {
            log.warn("[SelectionResolver] 取数失败，不推卡 scenario={}, sessionId={}, err={}",
                    scenario, sessionId, e.getMessage());
            return;
        }
        if (options.isEmpty()) {
            log.info("[SelectionResolver] 候选为空，不推卡 scenario={}, sessionId={}", scenario, sessionId);
            return;
        }
        boolean multi = SelectionCardFactory.multiSelectOf(scenario);
        String title = SelectionCardFactory.titleOf(scenario);
        if (!AgentStreamEvents.selectionRequest(sink, scenario, title, multi, options)) {
            log.warn("[SelectionResolver] 事件 sink 不可用，推卡失败 scenario={}, sessionId={}", scenario, sessionId);
            return;
        }
        log.info("[SelectionResolver] 已确定性推卡 scenario={}, count={}, multi={}", scenario, options.size(), multi);
    }

    private List<SelectionOptionVO> buildOptions(String sessionId, String scenario) throws Exception {
        switch (scenario) {
            case SCENE_SPACE:
                return selectionCardFactory.spaceOptions(mcpToolInvoker.call("listOntologySpaces", "{}"));
            case SCENE_DATASOURCE:
                return selectionCardFactory.datasourceOptions(mcpToolInvoker.call("searchDatasources", "{\"keyword\":\"\"}"));
            case SCENE_PROPERTIES: {
                String proposed = sessionStateStore.getStage(sessionId, "properties_proposed");
                if (proposed == null) {
                    throw new IllegalStateException("缺少 properties_proposed 状态块");
                }
                return selectionCardFactory.propertiesOptions(proposed);
            }
            case SCENE_RELATIONS: {
                String proposed = sessionStateStore.getStage(sessionId, "relations_proposed");
                if (proposed == null) {
                    throw new IllegalStateException("缺少 relations_proposed 状态块");
                }
                return selectionCardFactory.relationsOptions(proposed);
            }
            default:
                return List.of();
        }
    }
}
