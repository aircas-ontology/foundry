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
 *       第0步→space_select、第2步或第3步→datasource_select（已上传参考文档时第3步不再强制推数据源卡，支持
 *       文档-only 构建）。本类扫描回复中<b>全部</b>横幅编号（而非只看第一个），
 *       以覆盖"确认上一步 + 提及下一步"同轮出现多个横幅的场景。</li>
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
     * @param sessionId   会话 id
     * @param replyText   本轮模型完整回复文本（用于解析步骤横幅 / proposed / 兼容标记）
     * @param datasourceId 当前请求携带的数据源 id（前端已选数据源），可为 null
     * @param spaceId     当前请求携带的空间 id（前端已选空间），可为 null
     * @param sink        本次请求的事件 sink
     */
    public void resolveForRound(String sessionId, String replyText, Integer datasourceId, Integer spaceId, Sinks.Many<AgentChatStreamVO> sink) {
        // 先从横幅推断已完成的阶段并落盘（后端兜底，不依赖模型输出 stage JSON）
        inferAndRecordCompletedStages(sessionId, replyText, spaceId, datasourceId);

        // 候选场景（有序去重）：状态机推断优先，其次模型主动标记
        Set<String> candidates = new LinkedHashSet<>();
        String inferred = inferScenario(sessionId, replyText, datasourceId, spaceId);
        if (inferred != null) {
            candidates.add(inferred);
        }
        candidates.addAll(sessionStageCollector.extractSelectionScenarios(replyText));

        for (String scenario : candidates) {
            if (!SelectionCardFactory.supports(scenario)) {
                continue;
            }
            if (isSatisfiedWithParams(sessionId, scenario, datasourceId, spaceId)) {
                log.debug("[SelectionResolver] {} 已满足（selected 已存在或前端已传），跳过推卡", scenario);
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
     *   <li>回复横幅含第0步（扫描全部横幅）且未选空间 → space_select</li>
     *   <li>回复横幅含第2步或第3步，且空间已选、未选数据源 → datasource_select</li>
     * </ul>
     *
     * @param datasourceId 当前请求携带的数据源 id（前端已选数据源），可为 null
     * @param spaceId     当前请求携带的空间 id（前端已选空间），可为 null
     * @return 推断出的 scenario；无可推断时返回 {@code null}
     */
    private String inferScenario(String sessionId, String replyText, Integer datasourceId, Integer spaceId) {
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
        // 第0/2步：靠步骤横幅定位（扫描回复中全部横幅,模型对横幅遵从度高）
        Set<Integer> steps = parseAllSteps(replyText);
        if (steps.contains(0) && sessionStateStore.getStage(sessionId, "space_selected") == null && spaceId == null) {
            return SCENE_SPACE;
        }
        // 数据源：横幅含第2步（资料导入步）或第3步（扫表步，兜底模型跳过了第2步横幅的情况）；
        // 均需 space 已选定（状态块或前端入参）、datasource 尚未选中（状态块和前端入参皆无），避免误推
        // 额外检查：若已进入属性/关系阶段（properties_proposed/relations_proposed 存在），则不再回退推数据源
        // 文档-only 模式：已上传参考文档时，第3步横幅不再强制推数据源卡（用户可只用文档构建对象）；
        // 第2步横幅仍推，保留"文档+数据源"同时使用的入口，用户可忽略卡片
        // 注意：不再使用 replyText.contains("数据源") 等关键词兜底——模型在任意步骤提及"数据源"都会误触发推卡
        boolean spaceSelected = sessionStateStore.getStage(sessionId, "space_selected") != null || spaceId != null;
        boolean datasourceSelected = sessionStateStore.getStage(sessionId, "datasource_selected") != null || datasourceId != null;
        boolean documentUploaded = sessionStateStore.getStage(sessionId, SessionStateStore.STAGE_DOCUMENT_UPLOADED) != null;
        boolean inPropertiesOrLater = sessionStateStore.getStage(sessionId, "properties_proposed") != null
                || sessionStateStore.getStage(sessionId, "relations_proposed") != null;
        if (spaceSelected && !datasourceSelected && !inPropertiesOrLater
                && (steps.contains(2) || (steps.contains(3) && !documentUploaded))) {
            return SCENE_DATASOURCE;
        }
        return null;
    }

    /**
     * 从回复横幅推断已完成的阶段并记录到状态存储（后端兜底，不依赖模型输出 stage JSON）。
     *
     * <p>模型可能忘记输出 {@code space_selected} / {@code object_defined} 等 JSON，
     * 但步骤横幅是稳定信号：出现第 1 步横幅 ⇒ 第 0 步（选空间）已完成；
     * 出现第 2 步横幅 ⇒ 第 1 步（定义对象）已完成。本方法据此补录状态，
     * 使下一轮模型注入的 system 消息中包含已完成阶段，避免重复确认。</p>
     *
     * <p>补录时尽量用前端入参的 spaceId/datasourceId 填充，避免产生不含具体 id 的占位符 JSON
     * （如 {@code {"stage":"space_selected"}}），否则下一轮模型看到不完整状态会反复纠结。</p>
     */
    private void inferAndRecordCompletedStages(String sessionId, String replyText, Integer spaceId, Integer datasourceId) {
        if (replyText == null || replyText.isBlank()) {
            return;
        }
        Set<Integer> steps = parseAllSteps(replyText);
        // 第1步+横幅出现 ⇒ 空间已选（但状态存储尚无记录）
        if (steps.stream().anyMatch(s -> s >= 1)
                && sessionStateStore.getStage(sessionId, "space_selected") == null) {
            String json = extractStageJson(replyText, "space_selected");
            if (json == null && spaceId != null) {
                // 模型没输出 space_selected JSON，但前端传了 spaceId，用前端信息补全
                json = "{\"stage\":\"space_selected\",\"spaceId\":" + spaceId + "}";
            }
            sessionStateStore.record(sessionId, "space_selected",
                    json != null ? json : "{\"stage\":\"space_selected\"}");
            log.info("[SelectionResolver] 横幅推断 space_selected 已补录 sessionId={}, spaceId={}", sessionId, spaceId);
        }
        // 第2步+横幅出现 ⇒ 对象已定义
        if (steps.stream().anyMatch(s -> s >= 2)
                && sessionStateStore.getStage(sessionId, "object_defined") == null) {
            String json = extractStageJson(replyText, "object_defined");
            sessionStateStore.record(sessionId, "object_defined",
                    json != null ? json : "{\"stage\":\"object_defined\"}");
            log.info("[SelectionResolver] 横幅推断 object_defined 已补录 sessionId={}", sessionId);
        }
    }

    /** 尝试从回复文本中提取指定 stage 的 JSON 块；找不到则返回 null。 */
    private String extractStageJson(String replyText, String stageName) {
        for (String json : SessionStageCollector.extractJsonObjects(replyText)) {
            if (json.contains("\"stage\"") && json.contains("\"" + stageName + "\"")) {
                return json;
            }
        }
        return null;
    }

    /** 收集回复中出现的所有步骤横幅编号（【第N步/共11步】）；无横幅时返回空集合。 */
    private Set<Integer> parseAllSteps(String replyText) {
        Set<Integer> steps = new LinkedHashSet<>();
        Matcher m = STEP_BANNER.matcher(replyText);
        while (m.find()) {
            try {
                steps.add(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
                // 单个横幅编号解析失败则跳过，不影响其余横幅
            }
        }
        return steps;
    }

    /**
     * 该场景是否已完成（支持从 session 状态或前端入参判断）。
     * 与仅检查 session 状态不同，本方法额外检查前端入参，覆盖用户已选但模型尚未输出 JSON 确认块的场景。
     */
    private boolean isSatisfiedWithParams(String sessionId, String scenario, Integer datasourceId, Integer spaceId) {
        switch (scenario) {
            case SCENE_SPACE:
                return sessionStateStore.getStage(sessionId, "space_selected") != null || spaceId != null;
            case SCENE_DATASOURCE:
                return sessionStateStore.getStage(sessionId, "datasource_selected") != null || datasourceId != null;
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
