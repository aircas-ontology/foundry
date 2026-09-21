package com.aircas.ptr.foundry.agent.server.config;

import com.aircas.ptr.foundry.agent.server.stream.EventEmittingToolCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * ChatClient 配置。
 *
 * <p>基于 DeepSeek（OpenAI 兼容接口）自动装配的 {@link ChatClient.Builder} 构建对话客户端。工具不再硬编码：
 * 由 Spring AI MCP Client 在启动时连接 ontology-server 的 MCP Server，经 {@code initialize} 握手 +
 * {@code tools/list} 动态发现，自动装配为 {@link ToolCallbackProvider}；本类取出其 {@link ToolCallback}
 * 并用 {@link EventEmittingToolCallback} 装饰后注册，使大模型具备按需调用 ontology-server 能力
 * （Function Calling / Tool Calling），且工具增减无需改动 agent-server。</p>
 *
 * <p>模型与密钥由 {@code application.yml} 的 {@code spring.ai.openai.*} 配置
 * （base-url 指向 https://api.deepseek.com，默认模型 deepseek-v4-flash，密钥可走环境变量 LLM_API_KEY）。</p>
 */
@Configuration
public class ChatClientConfig {

    /**
     * 默认系统提示词：约束助手角色与回答风格。
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是 Foundry 本体平台的智能助手，负责帮助用户查询和理解本体（Ontology）知识图谱、
            本体元数据、分组等信息。

            工作原则：
            1. 当用户的问题需要真实数据时，主动调用可用的工具（如搜索本体、查询本体详情、查询空间分组、查询数据源）获取，
               不要凭空编造本体名称、数量或标识。
            2. 工具返回结果后，用简洁、准确的中文总结回答；涉及数量、标识等关键信息时如实呈现。
            3. 若工具返回为空或调用失败，明确告知用户未查询到相关数据，而不是虚构内容。
            4. 与本体数据无关的闲聊，正常友好回答即可。

            功能二「新建本体对象」工作流：
            当用户输入一段话要求创建本体对象时：
            1. 检查当前会话上下文中的 datasourceId 和 spaceId：
               - 若 datasourceId 为空：回复"请先选择数据源（功能一）"，不调用工具。
               - 若 spaceId 为空：回复"请先选择空间"，不调用工具。
            2. 若两者都有值：
               a. 调用「扫描数据源表注释」工具（入参为数据源 id）获取该数据源的表名 + 表注释列表。
               b. 调用「查询空间分类列表」工具（入参为空间 id）获取该空间下的分类列表。
               c. 从用户段落中提取对象的基本定义（名称、标识、描述）。
               d. 将提取的定义与表注释比对，选择最匹配的表作为对象来源。
               e. 从分类列表中选择最合适的分类；若无合适分类，category 返回"无"。
            3. 以结构化 JSON 返回结果，格式如下（不要输出其他内容）：
               ```json
               {
                 "objectName": "对象名称",
                 "objectIdentifier": "对象标识（英文，驼峰或下划线）",
                 "objectDescription": "对象描述",
                 "category": "分类名称或无"
               }
               ```
            """;

    /**
     * 会话记忆：滑动窗口保留最近若干条消息，按 conversationId（前端 sessionId）隔离多轮上下文。
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 ToolCallbackProvider mcpToolCallbackProvider,
                                 ChatMemory chatMemory) {
        // MCP Client 启动时已从 ontology-server 动态发现工具并装配为 ToolCallbackProvider；
        // 取出回调后用装饰器包装，以在调用前后推送 tool_call/tool_result 流式事件
        ToolCallback[] rawCallbacks = mcpToolCallbackProvider.getToolCallbacks();
        ToolCallback[] eventEmittingCallbacks = Arrays.stream(rawCallbacks)
                .map(EventEmittingToolCallback::new)
                .toArray(ToolCallback[]::new);

        return builder
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultToolCallbacks(eventEmittingCallbacks)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
