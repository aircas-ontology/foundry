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
 * 也不肯输出 {@code request_selection} 文本标记，甚至可能漏掉"下一步"横幅——任何"依赖模型显式触发"的
 * 方案都会概率性失效。因此本类为<b>纯状态机</b>：依据模型稳定信号 + 用户消息意图决定推卡，
 * 模型无需做任何 function-calling 或特殊标记。</p>
 *
 * <p>可依赖的信号：
 * <ol>
 *   <li><b>步骤横幅</b>：提示词要求每步开头输出 {@code 【第N步/共11步】}（第0步→space_select、
 *       第2步或第3步→datasource_select；已上传参考文档时第3步不再强制推数据源卡，支持文档-only 构建）。
 *       扫描回复中<b>全部</b>横幅编号，覆盖"确认上一步 + 提及下一步"同轮出现多个横幅的场景。</li>
 *   <li><b>用户消息意图</b>（横幅漏发时的兜底）：
 *       短确认（"确认/好的/继续"等）且空间已选、定义未落盘 ⇒ 判定第1步完成，补录 {@code object_confirmed}
 *       并推数据源卡；"不选数据源/只用文档" ⇒ 记 {@code datasource_skipped}，停止推卡并引导上传；
 *       "（再）选/换数据源" ⇒ 清除 skipped 与推卡 marker，强制重推。</li>
 *   <li><b>推卡去重 marker</b>（{@code datasource_card_pushed} / {@code space_card_pushed}）：
 *       同一"未选择"状态下卡片只推一次，杜绝每轮回复尾部重复堆同一张卡。</li>
 *   <li><b>已持久化的 proposed 状态块</b>：第4/5步模型稳定输出 {@code properties_proposed} /
 *       {@code relations_proposed} JSON，据此推 properties_select / relations_select（不受 marker 影响）。</li>
 * </ol>
 * 另兼容模型主动输出的 {@code request_selection} 标记（若有则一并纳入候选）。</p>
 *
 * <p>取数来源：space/datasource 经 {@link McpToolInvoker} 程序化调 MCP 工具；properties/relations 从
 * {@link SessionStateStore} 回查。候选为空或取数失败时<b>不推卡</b>、不记 marker（下轮条件仍满足可重试），
 * 但会向回复流追加一句可见的系统提示，避免模型声称"上方有卡片"而用户看不到。
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

    /** 用户明确拒绝选数据源（如"我不选数据源""只用文档"）→ 记 datasource_skipped，停止推数据源卡。 */
    private static final Pattern REFUSE_DATASOURCE = Pattern.compile(
            "不(想|用|要|选|需要)(择|一个|个)?数据源|跳过数据源|只(用|靠|通过|使用)(上传的)?(参考)?文档|仅(用|靠|通过|使用)(参考)?文档|文档\\s*only",
            Pattern.CASE_INSENSITIVE);

    /** 用户再次主动要求选数据源（如"再选一个数据源""数据源卡片呢"）→ 清除 skipped/marker 强制重推。 */
    private static final Pattern REQUEST_DATASOURCE = Pattern.compile(
            "(重新|再|另外|我要|我想)?(选|选择|指定|换|推|查|看)(一)?(个|下)?(目标|可用)?数据源|数据源(卡片|列表)",
            Pattern.CASE_INSENSITIVE);

    /** 短确认消息（第1步定义确认等）：整句精确匹配且长度受限，避免长句里的客套词被误判。 */
    private static final Pattern CONFIRM_MESSAGE = Pattern.compile(
            "^(确认|确认了|确认无误|没问题|没有问题|好的|好了|可以|行|对的|对|是|是的|嗯+|ok|继续|就这样|开始吧)[。！!？?~～\\s]*$",
            Pattern.CASE_INSENSITIVE);

    /** 短确认判定上限：超过该长度的消息视为携带其他诉求，不做确定性推进。 */
    private static final int CONFIRM_MAX_LENGTH = 12;

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
     * @param sessionId      会话 id
     * @param userMessage    本轮用户消息原文（用于短确认推进 / 数据源意愿识别）
     * @param replyText      本轮模型完整回复文本（用于解析步骤横幅 / proposed / 兼容标记）
     * @param datasourceId   当前请求携带的数据源 id（前端已选数据源），可为 null
     * @param spaceId        当前请求携带的空间 id（前端已选空间），可为 null
     * @param sink           本次请求的事件 sink
     */
    public void resolveForRound(String sessionId, String userMessage, String replyText,
                                Integer datasourceId, Integer spaceId, Sinks.Many<AgentChatStreamVO> sink) {
        boolean datasourceSelected = sessionStateStore.getStage(sessionId, "datasource_selected") != null
                || datasourceId != null;
        applyUserIntent(sessionId, userMessage, datasourceSelected);

        // 先从横幅推断已完成的阶段并落盘（后端兜底，不依赖模型输出 stage JSON）
        inferAndRecordCompletedStages(sessionId, replyText, spaceId, datasourceId);

        // 候选场景（有序去重）：用户意图驱动优先，其次横幅状态机推断，最后模型主动标记
        Set<String> candidates = new LinkedHashSet<>();
        if (shouldAdvanceToImportStage(sessionId, userMessage, datasourceId, spaceId)) {
            sessionStateStore.record(sessionId, SessionStateStore.STAGE_OBJECT_CONFIRMED,
                    "{\"stage\":\"object_confirmed\"}");
            candidates.add(SCENE_DATASOURCE);
            log.info("[SelectionResolver] 短确认判定第1步完成，补录 object_confirmed 并候选推数据源卡 sessionId={}", sessionId);
        }
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
            if (isPushSuppressed(sessionId, scenario)) {
                log.debug("[SelectionResolver] {} 被抑制（已推过或用户已拒绝），本轮不重复推卡 sessionId={}", scenario, sessionId);
                continue;
            }
            resolveAndEmit(sessionId, scenario, sink);
            // 流程严格串行，一轮至多推一张卡；候选处理即结束（无论成败，失败已给可见提示，下轮条件满足可重试）
            return;
        }
    }

    /**
     * 解析用户消息对数据源卡片的意愿并落到状态块：拒绝 ⇒ datasource_skipped + 清 marker；
     * 主动要求 ⇒ 清 skipped + 清 marker（使本轮或后续候选能通过抑制检查，强制重推）。
     */
    private void applyUserIntent(String sessionId, String userMessage, boolean datasourceSelected) {
        String msg = trim(userMessage);
        if (msg.isEmpty()) {
            return;
        }
        if (REFUSE_DATASOURCE.matcher(msg).find()) {
            sessionStateStore.record(sessionId, SessionStateStore.STAGE_DATASOURCE_SKIPPED,
                    "{\"stage\":\"datasource_skipped\",\"source\":\"user_message\"}");
            sessionStateStore.remove(sessionId, SessionStateStore.STAGE_DATASOURCE_CARD_PUSHED);
            log.info("[SelectionResolver] 用户消息判定不选数据源，记 datasource_skipped sessionId={}", sessionId);
        } else if (!datasourceSelected && REQUEST_DATASOURCE.matcher(msg).find()) {
            sessionStateStore.remove(sessionId, SessionStateStore.STAGE_DATASOURCE_SKIPPED);
            sessionStateStore.remove(sessionId, SessionStateStore.STAGE_DATASOURCE_CARD_PUSHED);
            log.info("[SelectionResolver] 用户消息判定要选数据源，清除 skipped/marker sessionId={}", sessionId);
        }
    }

    /**
     * 短确认推进判定：用户一句"确认/好的/继续"且系统里空间已选、定义未确认落盘、数据源未选、文档未传
     * ⇒ 模型漏发第2步横幅的概率场景，后端确定性判定第1步完成。
     */
    private boolean shouldAdvanceToImportStage(String sessionId, String userMessage, Integer datasourceId, Integer spaceId) {
        String msg = trim(userMessage);
        if (msg.isEmpty() || msg.length() > CONFIRM_MAX_LENGTH || !CONFIRM_MESSAGE.matcher(msg).matches()) {
            return false;
        }
        boolean spaceSelected = sessionStateStore.getStage(sessionId, "space_selected") != null || spaceId != null;
        boolean definitionSettled = sessionStateStore.getStage(sessionId, SessionStateStore.STAGE_OBJECT_CONFIRMED) != null
                || sessionStateStore.getStage(sessionId, "object_defined") != null;
        boolean datasourceSelected = sessionStateStore.getStage(sessionId, "datasource_selected") != null || datasourceId != null;
        boolean documentUploaded = sessionStateStore.getStage(sessionId, SessionStateStore.STAGE_DOCUMENT_UPLOADED) != null;
        return spaceSelected && !definitionSettled && !datasourceSelected && !documentUploaded;
    }

    /**
     * 该 scenario 当前是否应抑制推卡：datasource/space 卡在同一"未选择"状态下只推一次；
     * 用户改主意（要选数据源）时 marker 已被 applyUserIntent 清除，自然解除抑制。
     * properties/relations 卡由 proposed JSON 驱动（每次 proposed 内容不同），不做 marker 抑制。
     */
    private boolean isPushSuppressed(String sessionId, String scenario) {
        switch (scenario) {
            case SCENE_DATASOURCE:
                return sessionStateStore.getStage(sessionId, SessionStateStore.STAGE_DATASOURCE_SKIPPED) != null
                        || sessionStateStore.getStage(sessionId, SessionStateStore.STAGE_DATASOURCE_CARD_PUSHED) != null;
            case SCENE_SPACE:
                return sessionStateStore.getStage(sessionId, SessionStateStore.STAGE_SPACE_CARD_PUSHED) != null;
            default:
                return false;
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
        // 第2步横幅仍进入候选，是否实际推卡由 isPushSuppressed 决定（推过一次或被用户拒绝后不再重复）
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
     * 出现第 2 步横幅 ⇒ 第 1 步（定义对象）已确认。本方法据此补录状态，
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
        // 第2步+横幅出现 ⇒ 对象已定义、第1步已确认
        if (steps.stream().anyMatch(s -> s >= 2)) {
            if (sessionStateStore.getStage(sessionId, "object_defined") == null) {
                String json = extractStageJson(replyText, "object_defined");
                sessionStateStore.record(sessionId, "object_defined",
                        json != null ? json : "{\"stage\":\"object_defined\"}");
                log.info("[SelectionResolver] 横幅推断 object_defined 已补录 sessionId={}", sessionId);
            }
            if (sessionStateStore.getStage(sessionId, SessionStateStore.STAGE_OBJECT_CONFIRMED) == null) {
                sessionStateStore.record(sessionId, SessionStateStore.STAGE_OBJECT_CONFIRMED,
                        "{\"stage\":\"object_confirmed\"}");
            }
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
     * 按 scenario 确定性取数并推 {@code selection_request}；成功后记推卡 marker，
     * 失败/空候选时不记 marker 并向流追加可见提示（避免"模型说上方有卡片、实际没有"的静默错位）。
     */
    private void resolveAndEmit(String sessionId, String scenario, Sinks.Many<AgentChatStreamVO> sink) {
        List<SelectionOptionVO> options;
        try {
            options = buildOptions(sessionId, scenario);
        } catch (Exception e) {
            log.warn("[SelectionResolver] 取数失败，不推卡 scenario={}, sessionId={}, err={}",
                    scenario, sessionId, e.getMessage());
            emitFailureHint(sink, scenario, "获取失败（" + e.getMessage() + "）");
            return;
        }
        if (options.isEmpty()) {
            log.info("[SelectionResolver] 候选为空，不推卡 scenario={}, sessionId={}", scenario, sessionId);
            emitFailureHint(sink, scenario, "暂无可选项");
            return;
        }
        boolean multi = SelectionCardFactory.multiSelectOf(scenario);
        String title = SelectionCardFactory.titleOf(scenario);
        if (!AgentStreamEvents.selectionRequest(sink, scenario, title, multi, options)) {
            log.warn("[SelectionResolver] 事件 sink 不可用，推卡失败 scenario={}, sessionId={}", scenario, sessionId);
            return;
        }
        recordPushedMarker(sessionId, scenario);
        log.info("[SelectionResolver] 已确定性推卡 scenario={}, count={}, multi={}", scenario, options.size(), multi);
    }

    /** 推卡成功后记录 marker：同一"未选择"状态下不再重复推同一张卡。 */
    private void recordPushedMarker(String sessionId, String scenario) {
        if (SCENE_DATASOURCE.equals(scenario)) {
            sessionStateStore.record(sessionId, SessionStateStore.STAGE_DATASOURCE_CARD_PUSHED,
                    "{\"stage\":\"datasource_card_pushed\"}");
        } else if (SCENE_SPACE.equals(scenario)) {
            sessionStateStore.record(sessionId, SessionStateStore.STAGE_SPACE_CARD_PUSHED,
                    "{\"stage\":\"space_card_pushed\"}");
        }
    }

    /** 向回复流追加一句可见的系统提示，说明卡片未出现的原因；sink 不可用时静默（如同步接口路径）。 */
    private static void emitFailureHint(Sinks.Many<AgentChatStreamVO> sink, String scenario, String reason) {
        if (sink == null) {
            return;
        }
        String noun = SCENE_SPACE.equals(scenario) ? "空间列表" : "数据源列表";
        sink.tryEmitNext(AgentChatStreamVO.content(
                "\n\n（系统提示：" + noun + reason + "，选择卡片未显示，可稍后重试或告知名称继续）"));
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

    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }
}
