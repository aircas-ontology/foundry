# 内网大模型调用 "type missing" 排查与修复指南

## 当前版本基线

> **去内网之前务必确认以下版本已匹配，内网无法联网改版本。**

| 组件 | 当前版本 | 说明 |
|------|---------|------|
| JDK | 17 | `agent-server/pom.xml` → `<java.version>` |
| Spring Boot | 3.5.3 | 根 `pom.xml` → `spring-boot-starter-parent` |
| Spring AI | 1.1.2 | 根 `pom.xml` → `<spring-ai.version>` |
| Spring AI 传输 | `spring-ai-starter-model-openai` | OpenAI 兼容协议（对接 DeepSeek） |
| MCP Client | `spring-ai-starter-mcp-client-webflux` | WebFlux SSE 传输 |

### 版本兼容性说明

- **Spring AI 1.1.2** 生成的 tool 定义格式遵循 OpenAI 2024 年规范，每个 tool 顶层带 `"type": "function"`。
- 如果内网模型服务是基于较旧版本的 vLLM / FastAPI 自建，可能对 `type` 字段的校验更严格或格式要求不同。
- **不建议在内网改 Spring AI 版本**——改动牵涉整棵依赖树（Spring Boot 3.5.x 与 Spring AI 1.1.x 强绑定），且内网无 Maven 仓库。
- 如果确认是版本兼容问题，应在**外网开发机**上完成版本调整和全量测试，再打包部署到内网。

### 可选降级版本（仅在外网提前验证后用）

| Spring AI | Spring Boot | 差异点 |
|-----------|-------------|--------|
| 1.1.2（当前） | 3.5.3 | tool 定义带 `type: "function"`，支持 `tool_choice` 对象格式 |
| 1.0.0 | 3.4.x | tool 定义结构相同，但 `tool_choice` 仅支持字符串格式 |
| 0.8.1 | 3.2.x | 无 `ToolCallbackProvider` 自动装配，MCP 需手动注册 |

---

## 问题描述

Agent Server 调用内网 DeepSeek 模型时：
- **同步对话**：报 `"type missing, loc body, field required"` 错误
- **流式对话**：报 `400 Bad Request` 错误
- **外网调用**：同样的代码、同样的配置，调用 DeepSeek 官方 API 正常

错误信息 `"type missing, loc body, field required"` 是 Python FastAPI 框架的标准校验错误，表明内网网关/模型服务在解析请求体时发现某个必填的 `type` 字段缺失。

---

## 排查工具：三层日志拦截

项目已内置三层日志，在 `application-local.yml` 中开启 DEBUG 后生效：

| 层级 | 配置类 | 日志标识 | 看什么 |
|------|--------|---------|--------|
| ChatClient 层 | `SimpleLoggerAdvisor`（Spring AI 内置） | `o.s.a.c.c.advisor.SimpleLoggerAdvisor` | 完整 messages、model、**tools 定义**、options |
| HTTP 层（同步） | `WebClientConfig` 中的拦截器 | `ai.http.wire` `>>>` / `<<<` | 完整 HTTP 请求体/响应体/Headers |
| HTTP 层（流式） | `WebClientConfig` → `ExchangeFilterFunction` | `ai.http.wire` `~~~>` / `<~~~` | 请求 URL（排查双 /v1）、响应状态码 |

日志级别配置（`application-local.yml`）：

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
    ai.http.wire: DEBUG
```

---

## 排查流程（按顺序走）

### 第 1 步：摸清内网 API 环境

#### 1.1 确认 FastAPI / Pydantic 版本

内网模型服务基于 Python FastAPI，自带 Swagger 文档：

```
http://<内网模型地址>/docs          → Swagger UI（可视化查看）
http://<内网模型地址>/openapi.json  → 原始 OpenAPI 规范（JSON）
```

**版本探测方法**（到内网后依次执行）：

```bash
# 方法 1：看 OpenAPI 规范版本（判断 FastAPI 新旧）
curl http://<内网模型地址>/openapi.json
# 返回 JSON 中找 "openapi" 字段：
#   "openapi": "3.1.0" → FastAPI >= 0.99.0（较新，Pydantic v2）
#   "openapi": "3.0.3" → FastAPI < 0.99.0（较旧，Pydantic v1）

# 方法 2：看 Swagger UI 页面底部
# 浏览器打开 http://<内网模型地址>/docs
# 页面底部通常显示 "FastAPI v0.xxx.x" 版本号

# 方法 3：看报错格式反推（不需要额外请求）
# "type missing, loc body, field required" → Pydantic v2 → FastAPI >= 0.100.0
# "field required" (无 loc 路径)          → Pydantic v1 → FastAPI < 0.100.0

# 方法 4：问运维部署栈信息
# 内网模型通常基于以下框架之一部署：
#   vLLM       → 自带 OpenAI 兼容 API，版本决定支持的字段
#   FastChat   → 基于 FastAPI 的 OpenAI 兼容层
#   Xinference → 模型推理框架，内置 FastAPI
#   自研服务   → 直接基于 FastAPI 包装模型推理
```

**版本与行为对照表**：

| FastAPI 版本 | Pydantic | 校验严格度 | `type` 字段要求 |
|-------------|----------|-----------|----------------|
| >= 0.100.0 | v2 | **严格**：未知字段报错、必填字段缺失报 `loc body, field required` | tools 内每个层级都必须有 `type` |
| 0.99.0 | v2 过渡 | 中等 | 部分层级要求 `type` |
| < 0.99.0 | v1 | **宽松**：忽略未知字段 | 仅顶层要求 `type` |

> **结论**：当前报错 `"type missing, loc body, field required"` 说明内网用的是 **FastAPI >= 0.100.0 + Pydantic v2**，校验严格模式。

#### 1.2 确认底层推理框架

问运维或从启动日志中确认内网模型用的推理框架，不同框架对 OpenAI 兼容 API 的实现完整度不同：

| 推理框架 | Function Calling 支持 | `tool_choice` 支持 | 常见版本要求 |
|---------|----------------------|-------------------|-------------|
| vLLM | >= 0.5.0 支持 | >= 0.5.3 支持 | 建议 >= 0.6.0 |
| FastChat | 部分支持 | 不支持 | 已知有 `type` 校验问题 |
| Xinference | >= 0.10.0 支持 | 有限支持 | 建议 >= 0.11.0 |
| Ollama | >= 0.3.0 支持 | 不支持 | 建议 >= 0.4.0 |
| 自研 FastAPI | 取决于实现 | 取决于实现 | 需逐一排查 |

#### 1.3 查看请求体 Schema 中的 required 字段

拿到 OpenAPI 规范后，找到 `POST /chat/completions` 的请求体定义：

```bash
# 提取 chat/completions 的请求体 schema
curl http://<内网模型地址>/openapi.json | python -m json.tool | grep -A 50 "chat/completions"
```

重点关注：
- `required` 数组中列了哪些字段（这些是 FastAPI 强制校验的）
- `type` 字段出现在哪些层级（顶层？`tools[]` 内？`tools[].function.parameters` 内？）
- 是否定义了 `tools`、`tool_choice`、`stream` 字段（如果 schema 中根本没有这些字段，说明内网 API 不支持）

### 第 2 步：触发工具调用场景并抓日志

**关键**：简单对话（如"你好"）不会携带 `tools` 定义，而 "type missing" 大概率出在 tools 相关字段上。必须触发**工具调用场景**（如"有哪些本体空间"）。

从控制台日志中获取 `SimpleLoggerAdvisor` 输出的完整请求：

```
DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor : request: ChatClientRequest[
  prompt=Prompt{messages=[SystemMessage{...}, UserMessage{...}]},
  modelOptions=OpenAiChatOptions: {"model":"deepseek-v4-flash","temperature":0.3}
]
```

重点关注日志中的 `tools` 部分，检查每个 tool 的 JSON 结构。

### 第 3 步：curl 逐步加字段定位

从最简请求开始，逐步添加字段，找到触发报错的**具体字段**：

```bash
# ① 最简请求（应成功）
curl -X POST http://<内网地址>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <api-key>" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}]}'

# ② 加 temperature（应成功）
curl -X POST http://<内网地址>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <api-key>" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}],"temperature":0.3}'

# ②-b 加空 tools 数组（测试内网是否接受空数组）
curl -X POST http://<内网地址>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <api-key>" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}],"tools":[]}'

# ③ 加 tools（最可能触发 "type missing"）
#    从 SimpleLoggerAdvisor 日志中提取 tools JSON，附到请求里
curl -X POST http://<内网地址>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <api-key>" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"有哪些本体空间"}],"tools":[...从日志复制...]}'

# ④ 加 tool_choice
curl -X POST http://<内网地址>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <api-key>" \
  -d '{"model":"deepseek-v4-flash","messages":[...],"tools":[...],"tool_choice":"auto"}'

# ⑤ 加 stream
curl -X POST http://<内网地址>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <api-key>" \
  -d '{"model":"deepseek-v4-flash","messages":[...],"stream":true}'
```

**每加一个字段观察是否报错，哪个字段加上后报 "type missing"，问题就在它。**

> 特别注意步骤 ②-b：如果空 `tools: []` 就报错，说明内网 API 不接受空数组（场景 5a）；
> 如果空数组正常但带工具定义报错，说明是 tool 定义内容问题（场景 1/5b）。

---

## 问题场景与修复方案

### 场景 1：tools 定义中缺少 `type` 字段

**症状**：curl 第 ③ 步（加 tools）后报 `"type missing"`

**原因**：Spring AI 1.1.2 生成的 tool 定义中，某些层级的 `type` 字段可能不符合内网 API 的校验要求。

**检查方法**：

从 `SimpleLoggerAdvisor` 日志中提取 tools JSON，对比内网 API 文档（`/docs`）要求的格式。

Spring AI 1.1.2 发出的 tool 定义**正常结构**如下：

```json
{
  "type": "function",          // ← 顶层 type，标识工具类型
  "function": {
    "name": "listOntologySpaces",
    "description": "查询所有本体空间列表",
    "parameters": {
      "type": "object",        // ← parameters 内的 type，标识参数 schema 类型
      "properties": {
        "keyword": {
          "type": "string",    // ← 每个参数的 type
          "description": "搜索关键词"
        }
      }
    }
  }
}
```

**需要逐一检查的 `type` 字段**：

| 位置 | 字段路径 | 正常值 | 缺失后果 |
|------|---------|--------|---------|
| tool 顶层 | `tools[i].type` | `"function"` | FastAPI 报 `"type missing, loc body"` |
| parameters 层 | `tools[i].function.parameters.type` | `"object"` | 部分严格校验的网关会拒绝 |
| 每个参数 | `tools[i].function.parameters.properties.xxx.type` | `"string"` / `"number"` 等 | 参数类型不明 |

**修复方案**：

如果确认是 Spring AI 生成的 tool 定义缺少某个 `type` 字段，需要在外网开发机上修改代码，自定义 tool 定义的序列化输出。

**修改文件**：`agent-server/src/main/java/com/aircas/ptr/foundry/agent/server/stream/EventEmittingToolCallback.java`

在 `getToolDefinition()` 返回的 `ToolDefinition` 中，Spring AI 1.1.2 的 `inputSchema()` 方法生成的 JSON Schema 默认包含 `type: "object"`，但**外层 `type: "function"` 是由 Spring AI 框架在序列化时自动添加的**，不在 `ToolDefinition` 中。

如果内网 API 要求 tool 定义中必须显式包含 `type` 字段，而 Spring AI 发出的请求中确实缺失，说明问题出在 **Spring AI 框架序列化层**，不是我们的代码能直接控制的。

此时的解决路径（按优先级）：

1. **确认内网 API 文档是否真的要求 `type` 字段** — 有些 FastAPI 网关的 Swagger 文档标了 required 但实际不校验
2. **联系内网模型服务方放宽校验** — 这是最干净的解法
3. **在 HTTP 层拦截并补全 `type` 字段** — 见下方「方案 A」
4. **降级 Spring AI 版本** — 见上方版本兼容表，需在外网提前验证

#### 方案 A：HTTP 层拦截补全（推荐）

**思路**：在 WebClient / RestClient 层拦截发往模型的 HTTP 请求，检查 tools 数组中每个元素是否包含 `type` 字段，缺失则补上 `"type": "function"`。

**修改文件**：`agent-server/src/main/java/com/aircas/ptr/foundry/agent/server/config/WebClientConfig.java`

在现有的 `streamLoggingFilter()` 方法后面，增加一个请求体改写过滤器：

```java
// 在 WebClientConfig.java 的 webClientBuilder() 方法中，追加过滤器
return WebClient.builder()
        .exchangeStrategies(strategies)
        .filter(streamLoggingFilter())
        .filter(toolTypePatchFilter());   // ← 新增
```

新增过滤器方法：

```java
/**
 * 内网模型兼容：补全 tools 定义中缺失的 type 字段。
 *
 * <p>内网 FastAPI 网关校验严格，要求每个 tool 顶层必须有 "type": "function"；
 * Spring AI 某些版本序列化时可能遗漏。本过滤器在请求发出前检查并补全。</p>
 */
private ExchangeFilterFunction toolTypePatchFilter() {
    return (request, next) -> {
        // 仅对 POST 请求且 URL 包含 chat/completions 的生效
        if (!"POST".equalsIgnoreCase(request.method().name())
                || !request.url().getPath().contains("chat/completions")) {
            return next.exchange(request);
        }
        // 注意：WebClient 的 request body 是 Publisher<DataBuffer>，
        // 需要读取 → 改写 → 重新包装。实现较复杂，
        // 建议优先走方案 B（自定义 ToolDefinition 序列化）。
        return next.exchange(request);
    };
}
```

> **注意**：WebClient 的请求体改写涉及 reactive stream 操作，实现较复杂。更推荐方案 B。

#### 方案 B：自定义 ToolDefinition 包装（更简洁）

**思路**：在 `ChatClientConfig` 注册工具时，用自定义的 `ToolCallback` 包装器覆盖 `getToolDefinition()` 的返回值，确保 `inputSchema()` 生成的 JSON 中包含所有必需的 `type` 字段。

但实际上 Spring AI 1.1.2 的 `inputSchema()` 已经包含 `parameters.type = "object"`，外层 `type: "function"` 是框架在构建 HTTP 请求体时添加的。如果框架没加，说明可能是 **Spring AI 的 bug 或与内网 API 版本不兼容**。

**最务实的做法**：先用 curl 确认内网 API 到底要什么格式，再决定是改代码还是改部署。

---

### 场景 2：tool_choice 字段不兼容

**症状**：curl 第 ④ 步（加 tool_choice）后报错

**原因**：内网模型版本不支持 `tool_choice` 字段，或其格式与 Spring AI 1.1.2 发出的不同。

Spring AI 1.1.2 发出的 `tool_choice` 格式：

```json
// 格式 A：字符串（简单场景）
"tool_choice": "auto"

// 格式 B：对象（指定工具时）
"tool_choice": {
  "type": "function",
  "function": { "name": "listOntologySpaces" }
}
```

**修复**：

**修改文件**：`agent-server/src/main/java/com/aircas/ptr/foundry/agent/server/config/ChatClientConfig.java`

当前代码（第 217-228 行）：

```java
return builder
        .defaultSystem(DEFAULT_SYSTEM_PROMPT)
        .defaultToolCallbacks(eventEmittingCallbacks)
        .defaultTools(conversationTools)
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                new SimpleLoggerAdvisor()
        )
        .build();
```

如果内网不支持 `tool_choice`，需要在 `application-local.yml` 中确保**不设置** `tool-choice`：

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          # 不要设置 tool-choice，让 Spring AI 使用默认行为
          # tool-choice: auto   ← 如果有这行，注释掉
```

如果 Spring AI 框架默认就会发送 `tool_choice: "auto"`，而内网不接受任何 `tool_choice`，则需要通过自定义 `OpenAiChatOptions` 来显式排除该字段。

**修改文件**：`agent-server/src/main/java/com/aircas/ptr/foundry/agent/server/service/AgentChatService.java`

在 `chat()` 和 `chatStream()` 方法中，通过 `.options()` 传入自定义选项：

```java
// 在 AgentChatService.java 中注入一个配置标志
@Value("${llm.tool-choice-enabled:true}")
private boolean toolChoiceEnabled;

// 构建 options
OpenAiChatOptions options = OpenAiChatOptions.builder()
        .model(modelName)
        .temperature(0.3)
        // 仅当内网支持 tool_choice 时才设置
        // .toolChoice(toolChoiceEnabled ? "auto" : null)
        .build();
```

---

### 场景 3：内网模型不支持 Function Calling

**症状**：curl 第 ③ 步，只要请求里带 `tools` 字段就报 400，错误信息可能包含 "unsupported field" 或 "unknown field: tools"

**原因**：内网部署的模型版本较旧，不支持 Function Calling / Tool Calling。

**修复**：

**方案 A（推荐）：升级内网模型**
- 联系内网模型服务方升级到支持 Function Calling 的版本
- DeepSeek 从 v2 开始支持 tool calling

**方案 B：暂时禁用 tools**

**修改文件**：`agent-server/src/main/java/com/aircas/ptr/foundry/agent/server/config/ChatClientConfig.java`

注释掉工具注册（第 212-223 行）：

```java
// 临时禁用：内网模型不支持 Function Calling
// ToolCallback[] rawCallbacks = mcpToolCallbackProvider.getToolCallbacks();
// ToolCallback[] eventEmittingCallbacks = Arrays.stream(rawCallbacks)
//         .map(cb -> new EventEmittingToolCallback(cb, objectMapper))
//         .toArray(ToolCallback[]::new);

return builder
        .defaultSystem(DEFAULT_SYSTEM_PROMPT)
        // .defaultToolCallbacks(eventEmittingCallbacks)   // ← 注释
        // .defaultTools(conversationTools)                 // ← 注释
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                new SimpleLoggerAdvisor()
        )
        .build();
```

**影响**：禁用 tools 后，Agent 将无法调用 ontology-server 的任何工具（查空间、查本体、落库等），退化为纯对话模式。对象构建引导流程将不可用。

---

### 场景 4：URL 路径拼接错误（双 /v1）

**症状**：日志中请求 URL 变成 `http://xxx/v1/v1/chat/completions`，返回 404 或打到网关的其他路由上报错

**原因**：`base-url` 末尾带 `/v1`，`completions-path` 也以 `/v1` 开头，两者拼接出双 `/v1`。

**检查方法**：看 `ai.http.wire` 日志中 `~~~>` 打出的 URL。

**修复**：

**修改文件**：`application-local.yml`

两者不能同时带 `/v1`，选一种：

```yaml
# 方式 A：base-url 带 /v1，completions-path 不带（推荐）
spring:
  ai:
    openai:
      base-url: http://内网地址/v1
      chat:
        completions-path: /chat/completions

# 方式 B：base-url 不带 /v1，completions-path 带
spring:
  ai:
    openai:
      base-url: http://内网地址
      chat:
        completions-path: /v1/chat/completions
```

---

### 场景 5：空 tools 数组或简单对话携带 tools 导致报错

**症状**：
- 简单对话（如"你好"）也报 `"type missing"` 或 `400`
- curl 直接发不带 `tools` 的请求正常，带上 `"tools": []`（空数组）就报错

**原因**：Spring AI 1.1.2 的行为是——**只要 `ChatClient` 注册了 tools，每次请求都会带上完整的 `tools` 数组定义**，即使是"你好"这种不需要工具调用的简单对话。

两种可能的子场景：

| 子场景 | 请求内容 | 内网 API 反应 |
|--------|---------|-------------|
| 5a. 空 tools 数组 | `"tools": []` | 如果 Pydantic schema 定义了 `min_length=1` 或对空数组有特殊校验，会拒绝 |
| 5b. tools 中某项缺 type | `"tools": [{"function":{...}}]`（缺 `"type":"function"`） | Pydantic v2 严格校验 `Tool.type` 为 required，缺失即报 `"type missing"` |

**检查方法**：

从 `SimpleLoggerAdvisor` 日志中提取一次简单对话（如"你好"）的完整请求体，看 `tools` 字段的实际值：

```
DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor : request: ChatClientRequest[
  prompt=...,
  modelOptions=OpenAiChatOptions: {
    "model":"deepseek-v4-flash",
    "temperature":0.3,
    "tools":[...],          ← 看这里！简单对话是否也带了 tools
    "toolChoice":"auto"     ← 看这里！是否带了 tool_choice
  }
]
```

**修复方案**：

#### 方案 A：启动时检查工具列表，空则不注册（已实现）

**修改文件**：`agent-server/src/main/java/com/aircas/ptr/foundry/agent/server/config/ChatClientConfig.java`

在 `chatClient()` 方法中，检查 MCP 工具列表是否为空，空则不注册工具，这样请求中就不会携带 `tools` 字段：

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
                             ToolCallbackProvider mcpToolCallbackProvider,
                             ChatMemory chatMemory,
                             ConversationTools conversationTools,
                             ObjectMapper objectMapper) {
    ToolCallback[] rawCallbacks = mcpToolCallbackProvider.getToolCallbacks();

    var clientBuilder = builder
            .defaultSystem(DEFAULT_SYSTEM_PROMPT);

    // 仅当有实际 MCP 工具时才注册，避免发出空 tools 数组导致内网 FastAPI 校验报错
    if (rawCallbacks != null && rawCallbacks.length > 0) {
        ToolCallback[] eventEmittingCallbacks = Arrays.stream(rawCallbacks)
                .map(cb -> new EventEmittingToolCallback(cb, objectMapper))
                .toArray(ToolCallback[]::new);
        clientBuilder = clientBuilder.defaultToolCallbacks(eventEmittingCallbacks);
        clientBuilder = clientBuilder.defaultTools(conversationTools);
    }

    return clientBuilder
            .defaultAdvisors(
                    MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    new SimpleLoggerAdvisor()
            )
            .build();
}
```

**效果**：
- MCP 工具正常时（ontology-server 可达）：请求中携带 `tools` 数组，Agent 可调用工具
- MCP 工具为空时（ontology-server 未启动或连接失败）：请求中不携带 `tools` 字段，退化为纯对话模式

> **注意**：此方案是启动时一次性检查。如果运行中 MCP 连接断开，已注册的工具仍会保留。

#### 方案 B：全局禁用 tool_choice，仅保留 tools

如果内网 API 能接受 `tools` 但不接受 `tool_choice`：

**修改文件**：`application-local.yml`

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          # 不设置 tool-choice
          # tool-choice: auto   ← 注释掉或删除这行
```

#### 方案 C：确认内网 API 对空 tools 的容忍度

用 curl 测试：

```bash
# 测试 1：不带 tools（应成功）
curl -X POST http://<内网地址>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <api-key>" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}]}'

# 测试 2：带空 tools 数组（看是否报错）
curl -X POST http://<内网地址>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <api-key>" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}],"tools":[]}'

# 测试 3：带空 tools + tool_choice（看是否报错）
curl -X POST http://<内网地址>/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <api-key>" \
  -d '{"model":"deepseek-v4-flash","messages":[{"role":"user","content":"你好"}],"tools":[],"tool_choice":"auto"}'
```

如果测试 2 或 3 报错，说明内网 API 不接受空 tools 或 tool_choice，按方案 A 或 B 修复。

---

## 完整排查决策树

```
内网调用报错
│
├─ 报错信息包含 "type missing, loc body, field required"
│  │
│  ├─ 1. 先检查 URL（看 ai.http.wire 日志中 ~~~> 行）
│  │     ├─ URL 有双 /v1 → 场景 4：修复 base-url / completions-path 拼接
│  │     └─ URL 正常 → 继续 ↓
│  │
│  ├─ 2. curl 最简请求（只有 model + messages，不带 tools）
│  │     ├─ 失败 → 检查 API Key、Content-Type、模型名称、网络连通性
│  │     └─ 成功 → 说明模型本身没问题，问题在额外字段 → 继续 ↓
│  │
│  ├─ 3. 逐步加字段
│  │     ├─ 加 "tools":[] 后报错 → 场景 5a：内网不接受空 tools 数组
│  │     │
│  │     ├─ 加 tools（带工具定义）后报错
│  │     │     ├─ 错误是 "type missing" → 场景 1 或 场景 5b：tool 定义缺 type 字段
│  │     │     │     └─ 检查：简单对话是否也带了 tools？→ 场景 5
│  │     │     └─ 错误是 "unsupported field" → 场景 3：模型不支持 Function Calling
│  │     │
│  │     ├─ 加 tool_choice 后报错 → 场景 2：tool_choice 不兼容
│  │     │
│  │     └─ 加 stream 后报错 → SSE 兼容性问题
│  │           └─ 检查内网网关是否支持 chunked transfer encoding
│  │
│  ├─ 4. 检查 SimpleLoggerAdvisor 日志
│  │     ├─ 简单对话（"你好"）也带了 tools/tool_choice → 场景 5：按请求控制 tools 开关
│  │     └─ 仅工具调用场景才带 tools → 不是场景 5，继续看 tools 定义内容
│  │
│  └─ 5. 对比内网 API 文档（/docs 或 /openapi.json）
│        └─ 找出 Spring AI 请求体与内网 API 要求的差异，针对性修复
│
├─ 报错信息包含 "400 Bad Request"（流式场景）
│  └─ 同上方流程，流式场景额外检查 stream 字段兼容性
│
└─ 报错信息包含 "Connection refused" / "Timeout"
   └─ 网络问题：检查内网防火墙、代理配置、DNS 解析
```

---

## 去内网前的准备清单

> 内网无法联网，以下必须在外网提前完成。

### 必须做

- [ ] 确认 Spring AI 版本（当前 1.1.2）与内网模型版本的兼容性
- [ ] 在外网用 curl 模拟内网 API 格式测试（如果拿到了内网 API 文档）
- [ ] 打包完整部署包：`agent.jar` + `application-local.yml` + 内网模型地址配置
- [ ] 准备一份 `application-local.yml` 模板，内网只需改地址和 API Key

### 建议做

- [ ] 如果怀疑版本不兼容，在外网提前验证降级方案（见版本兼容表）
- [ ] 准备一个不依赖 tools 的降级版 `ChatClientConfig`，万一内网模型不支持 Function Calling 可快速切换
- [ ] 将排查用的 curl 命令整理成脚本，到内网后直接执行

### application-local.yml 模板

```yaml
spring:
  ai:
    openai:
      api-key: ${LLM_API_KEY:sk-xxx}
      base-url: ${LLM_BASE_URL:http://内网模型地址/v1}
      chat:
        completions-path: ${LLM_COMPLETIONS_PATH:/chat/completions}
        options:
          model: ${LLM_MODEL:deepseek-v4-flash}
          temperature: 0.3
          # tool-choice 默认不设置，内网确认支持后再打开
          # tool-choice: auto

# 排查时开启，定位完成后改回 INFO
logging:
  level:
    org.springframework.ai: DEBUG
    ai.http.wire: DEBUG
```

---

## 相关文件索引

| 文件 | 路径 | 作用 |
|------|------|------|
| ChatClient 配置 | `agent-server/.../config/ChatClientConfig.java` | 系统提示词 + 工具注册 + SimpleLoggerAdvisor |
| WebClient 配置 | `agent-server/.../config/WebClientConfig.java` | SSE 缓冲区 + 流式请求日志 + 过滤器扩展点 |
| 对话服务 | `agent-server/.../service/AgentChatService.java` | 同步/流式对话编排，可在此加 tool_choice 控制 |
| 工具装饰器 | `agent-server/.../stream/EventEmittingToolCallback.java` | tool_call/tool_result 事件推送 |
| MCP 工具调用 | `agent-server/.../tool/McpToolInvoker.java` | 程序化调用 MCP 工具（卡片取数用） |
| 本地配置 | `agent-server/src/main/resources/application-local.yml` | AI 配置（不入库，内网部署时填写） |
| 版本定义 | `pom.xml` → `<spring-ai.version>` | 当前 1.1.2 |
