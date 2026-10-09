package com.aircas.ptr.foundry.agent.server.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 流式对话事件视图对象。
 *
 * <p>SSE 流中的单个事件，前端按 {@link #type} 分流渲染：
 * {@code thinking / tool_call / tool_result} 累积进可折叠的"思考过程"面板，
 * {@code content} 逐字打进主回答气泡，{@code done} 表示结束，{@code error} 表示异常。</p>
 *
 * <p>以 JSON 对象承载（而非裸文本），避免 SSE {@code data:} 字段解析时吞掉首空格导致 Markdown 错位。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent 流式对话事件")
public class AgentChatStreamVO {

    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String TYPE_TOOL_RESULT = "tool_result";
    public static final String TYPE_CONTENT = "content";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_DONE = "done";
    /**
     * 选择卡片事件：由本地工具 {@code presentSelection} 触发，携带选项列表供前端渲染卡片；
     * 前端点击后需按约定句 {@code "已选择：<label>（id=<id>）"} 拼入下一轮 user message 回喂模型。
     */
    public static final String TYPE_SELECTION_REQUEST = "selection_request";

    @Schema(description = "事件类型：thinking/tool_call/tool_result/content/error/done", example = "content")
    private String type;

    @Schema(description = "文本内容（content/thinking/error 事件使用）")
    private String content;

    @Schema(description = "工具名（tool_call/tool_result 事件使用）", example = "searchDatasources")
    private String tool;

    @Schema(description = "工具入参摘要（tool_call 事件使用）")
    private String args;

    @Schema(description = "工具结果摘要（tool_result 事件使用）")
    private String summary;

    @Schema(description = "选择场景标识（selection_request 事件使用）：space_select / datasource_select 等",
            example = "space_select")
    private String scenario;

    @Schema(description = "卡片标题（selection_request 事件使用）", example = "请选择目标本体空间")
    private String title;

    @Schema(description = "是否允许多选（selection_request 事件使用），不传默认 false")
    private Boolean multiSelect;

    @Schema(description = "卡片选项列表（selection_request 事件使用）")
    private List<SelectionOptionVO> options;

    public static AgentChatStreamVO content(String content) {
        return AgentChatStreamVO.builder().type(TYPE_CONTENT).content(content).build();
    }

    public static AgentChatStreamVO thinking(String content) {
        return AgentChatStreamVO.builder().type(TYPE_THINKING).content(content).build();
    }

    public static AgentChatStreamVO toolCall(String tool, String args) {
        return AgentChatStreamVO.builder().type(TYPE_TOOL_CALL).tool(tool).args(args).build();
    }

    public static AgentChatStreamVO toolResult(String tool, String summary) {
        return AgentChatStreamVO.builder().type(TYPE_TOOL_RESULT).tool(tool).summary(summary).build();
    }

    public static AgentChatStreamVO error(String content) {
        return AgentChatStreamVO.builder().type(TYPE_ERROR).content(content).build();
    }

    public static AgentChatStreamVO done() {
        return AgentChatStreamVO.builder().type(TYPE_DONE).build();
    }

    /**
     * 选择卡片事件工厂：由 {@code SelectionTools#presentSelection} 调用，把结构化选项推送给前端。
     * 不经过 {@code AgentStreamEvents#truncate}，避免 options 列表被 2000 字上限截断。
     */
    public static AgentChatStreamVO selectionRequest(String scenario, String title,
                                                     boolean multiSelect, List<SelectionOptionVO> options) {
        return AgentChatStreamVO.builder()
                .type(TYPE_SELECTION_REQUEST)
                .scenario(scenario)
                .title(title)
                .multiSelect(multiSelect)
                .options(options)
                .build();
    }
}
