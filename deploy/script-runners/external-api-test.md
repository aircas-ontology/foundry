# 外部 API 函数算子 · 接口测试参数示例

> 统一前缀：`{BASE} = http://<host>:<port>/ontology`（context-path 为 `/ontology`，端口以本地 `application-local.yml` 为准）。
> 所有需要鉴权的接口需带请求头：`access-token: Bearer <token>`、`Content-Type: application/json`。
> 说明：`ontologySpaceId` 用你环境里真实存在的空间 id 替换；下文示例中的 `ext.httpbin.*` 等均可按需改名。
> 目标外部 API 使用 [httpbin.org](https://httpbin.org)（免费 HTTP 测试服务），确保测试机能访问外网。

---

## 0. 枚举取值速查

| 字段 | 可选值 |
|---|---|
| `type` | `EXTERNAL`（外部 API 算子） |
| `authType` | `none`（无认证，默认）、`api-key`（API Key 认证） |
| `method` | `GET` / `POST` / `PUT` / `DELETE`，不传默认 `POST` |
| `requestMappings[].location` | `path`（路径占位符）/ `query`（URL 参数）/ `header`（请求头）/ `body`（请求体），不传默认 `body` |
| `inputs[].type` / `outputs[].type` | `string` / `int` / `double` / `bool` / `object` / `list`，不传默认 `string` |
| `version` | 字符串 `x.y.z`，如 `1.0.0`；同 api 下必须递增且唯一 |

---

## 1. 创建外部 API 函数 `POST {BASE}/function`

### 1.1 GET + query 参数 + 响应映射

> 最简单的场景：GET 请求，入参走 query string，出参按响应 JSON 路径映射。

```json
{
  "functionApi": "ext.httpbin.get",
  "version": "1.0.0",
  "displayName": "HttpBin GET 测试",
  "description": "GET 请求，query 参数 + 响应字段映射",
  "type": "EXTERNAL",
  "ontologySpaceId": 1,
  "apiConfig": {
    "endpoint": "https://httpbin.org/get",
    "method": "GET",
    "authType": "none",
    "requestMappings": [
      { "parameterId": "name", "location": "query", "targetName": "name" }
    ],
    "responseMappings": [
      { "parameterId": "origin", "sourcePath": "origin" },
      { "parameterId": "url", "sourcePath": "url" }
    ],
    "inputs": [
      { "name": "name", "type": "string", "description": "你的名字" }
    ],
    "outputs": [
      { "name": "origin", "type": "string", "description": "客户端 IP" },
      { "name": "url", "type": "string", "description": "请求 URL" }
    ]
  }
}
```

### 1.2 POST + body 参数 + 嵌套响应映射

> POST 请求，入参走请求体 JSON 字段，出参从嵌套路径 `json.title` 取值。

```json
{
  "functionApi": "ext.httpbin.post",
  "version": "1.0.0",
  "displayName": "HttpBin POST 测试",
  "description": "POST 请求，body 参数 + 嵌套响应映射",
  "type": "EXTERNAL",
  "ontologySpaceId": 1,
  "apiConfig": {
    "endpoint": "https://httpbin.org/post",
    "method": "POST",
    "authType": "none",
    "requestMappings": [
      { "parameterId": "title", "location": "body", "targetName": "title" },
      { "parameterId": "content", "location": "body", "targetName": "content" }
    ],
    "responseMappings": [
      { "parameterId": "echo_title", "sourcePath": "json.title" },
      { "parameterId": "echo_content", "sourcePath": "json.content" }
    ],
    "inputs": [
      { "name": "title", "type": "string", "description": "标题" },
      { "name": "content", "type": "string", "description": "内容" }
    ],
    "outputs": [
      { "name": "echo_title", "type": "string", "description": "回显标题" },
      { "name": "echo_content", "type": "string", "description": "回显内容" }
    ]
  }
}
```

### 1.3 路径占位符（path 参数）

> endpoint 中用 `{userId}` / `{orderId}` 占位，请求映射 location=path 替换。

```json
{
  "functionApi": "ext.httpbin.path",
  "version": "1.0.0",
  "displayName": "HttpBin 路径参数测试",
  "description": "路径占位符替换",
  "type": "EXTERNAL",
  "ontologySpaceId": 1,
  "apiConfig": {
    "endpoint": "https://httpbin.org/anything/{userId}/orders/{orderId}",
    "method": "GET",
    "authType": "none",
    "requestMappings": [
      { "parameterId": "userId", "location": "path", "targetName": "userId" },
      { "parameterId": "orderId", "location": "path", "targetName": "orderId" }
    ],
    "responseMappings": [
      { "parameterId": "method", "sourcePath": "method" },
      { "parameterId": "request_url", "sourcePath": "url" }
    ],
    "inputs": [
      { "name": "userId", "type": "string", "description": "用户 ID" },
      { "name": "orderId", "type": "string", "description": "订单 ID" }
    ],
    "outputs": [
      { "name": "method", "type": "string", "description": "请求方法" },
      { "name": "request_url", "type": "string", "description": "实际请求 URL" }
    ]
  }
}
```

### 1.4 API-Key 鉴权

> `authType=api-key`，调用时自动在请求头注入 `X-API-Key: sk-test-abc123`。
> httpbin 的 `/headers` 接口会原样返回所有请求头，可验证认证头是否正确注入。

```json
{
  "functionApi": "ext.httpbin.apikey",
  "version": "1.0.0",
  "displayName": "HttpBin API-Key 鉴权测试",
  "description": "验证 API-Key 认证头注入",
  "type": "EXTERNAL",
  "ontologySpaceId": 1,
  "apiConfig": {
    "endpoint": "https://httpbin.org/headers",
    "method": "GET",
    "authType": "api-key",
    "apiKeyHeader": "X-API-Key",
    "apiKey": "sk-test-abc123",
    "responseMappings": [
      { "parameterId": "api_key_header", "sourcePath": "headers.X-Api-Key" }
    ],
    "outputs": [
      { "name": "api_key_header", "type": "string", "description": "服务端收到的 API Key" }
    ]
  }
}
```

### 1.5 字面量映射（literal 固定值）

> `parameterId` 以 `literal:` 开头表示不取入参，直接使用字面量值。
> 适合固定 header（如 `Accept: application/json`）等不依赖用户输入的场景。

```json
{
  "functionApi": "ext.httpbin.literal",
  "version": "1.0.0",
  "displayName": "HttpBin 字面量映射测试",
  "description": "literal 固定 header + 动态 header 混合",
  "type": "EXTERNAL",
  "ontologySpaceId": 1,
  "apiConfig": {
    "endpoint": "https://httpbin.org/headers",
    "method": "GET",
    "authType": "none",
    "requestMappings": [
      { "parameterId": "literal:application/json", "location": "header", "targetName": "Accept" },
      { "parameterId": "traceId", "location": "header", "targetName": "X-Trace-Id" }
    ],
    "responseMappings": [
      { "parameterId": "accept_header", "sourcePath": "headers.Accept" },
      { "parameterId": "trace_header", "sourcePath": "headers.X-Trace-Id" }
    ],
    "inputs": [
      { "name": "traceId", "type": "string", "description": "追踪 ID" }
    ],
    "outputs": [
      { "name": "accept_header", "type": "string", "description": "Accept 头（固定值）" },
      { "name": "trace_header", "type": "string", "description": "Trace 头（动态传入）" }
    ]
  }
}
```

### 1.6 不传 inputs/outputs（由映射自动推导）

> `inputs` 和 `outputs` 可省略，后端从 `requestMappings` / `responseMappings` 的 `parameterId` 自动推导 function_param。

```json
{
  "functionApi": "ext.httpbin.auto",
  "version": "1.0.0",
  "displayName": "HttpBin 自动推导参数",
  "description": "不传 inputs/outputs，由映射自动推导",
  "type": "EXTERNAL",
  "ontologySpaceId": 1,
  "apiConfig": {
    "endpoint": "https://httpbin.org/get",
    "method": "GET",
    "authType": "none",
    "requestMappings": [
      { "parameterId": "keyword", "location": "query", "targetName": "q" }
    ],
    "responseMappings": [
      { "parameterId": "origin", "sourcePath": "origin" }
    ]
  }
}
```

**预期校验（负例）**
- `apiConfig` 为空 → `外部API算子必须提供 apiConfig`。
- `endpoint` 为空 → `外部API算子必须提供接口地址 endpoint`。
- `version` 缺失 / 非 `x.y.z` → `版本号格式必须为 x.y.z`。
- `functionApi` 或 `displayName` 为空 → `functionApi is empty` / `displayName is empty`。
- 新创建的记录初始均为**未发布**（`publishStatus=0`）。

---

## 2. 更新外部 API 函数 `PUT {BASE}/function`

### 2.1 修改未发布版本（原地更新 apiConfig）

```json
{
  "functionApi": "ext.httpbin.get",
  "version": "1.0.0",
  "description": "修改说明：增加 age 参数",
  "apiConfig": {
    "endpoint": "https://httpbin.org/get",
    "method": "GET",
    "authType": "none",
    "requestMappings": [
      { "parameterId": "name", "location": "query", "targetName": "name" },
      { "parameterId": "age", "location": "query", "targetName": "age" }
    ],
    "responseMappings": [
      { "parameterId": "origin", "sourcePath": "origin" },
      { "parameterId": "url", "sourcePath": "url" }
    ],
    "inputs": [
      { "name": "name", "type": "string", "description": "你的名字" },
      { "name": "age", "type": "string", "description": "年龄" }
    ],
    "outputs": [
      { "name": "origin", "type": "string", "description": "客户端 IP" },
      { "name": "url", "type": "string", "description": "请求 URL" }
    ]
  }
}
```

- `version` **必填**，仅用于**定位**要改的未发布版本（修改不会变更版本号）。
- `functionApi` 与 `displayName` **改不动**（服务层不赋值 + PO `updateStrategy=NEVER`）。
- 未重新提交 `apiConfig` 时沿用库中已存定义，不会清空。
- 更新会先删旧 function_param 再按新 apiConfig 重建。

**预期校验（负例）**
- 目标版本已发布 → `已发布的函数不能修改，请先下线：ext.httpbin.get`。
- 目标 api 被任意行为关联 → `该函数已被行为关联，不能修改：ext.httpbin.get`。

---

## 3. 发布 / 下线

### 3.1 发布（未发布 → 已发布）`POST {BASE}/function/publish`
```
POST {BASE}/function/publish?functionApi=ext.httpbin.get&version=1.0.0
```
- `version` **必填**（格式 x.y.z），精确指定要发布的版本。
- 已是发布态再发 → `该版本已是发布状态：ext.httpbin.get 1.0.0`。

### 3.2 下线（已发布 → 未发布）`POST {BASE}/function/unpublish`
```
POST {BASE}/function/unpublish?functionApi=ext.httpbin.get&version=1.0.0
```
- `version` **必填**。
- 非发布态 → `该版本不是发布状态：ext.httpbin.get 1.0.0`。
- 被行为关联 → `该函数已被行为关联，不能下线：ext.httpbin.get`。

---

## 4. 删除函数 `DELETE {BASE}/function/delete`
```
DELETE {BASE}/function/delete?functionApi=ext.httpbin.get&version=1.0.0
```
- `version` **必填**。
- 仅**未发布**且**未被行为关联**可删。
- 已发布 → 先下线再删；被引用 → `该函数已被行为关联`。

---

## 5. 查询列表 `GET {BASE}/function/list`
```
GET {BASE}/function/list?ontologySpaceId=1&type=EXTERNAL&pageNum=1&pageSize=10
```
| 参数 | 必填 | 说明 |
|---|---|---|
| `ontologySpaceId` | 否 | 空间 id 过滤 |
| `displayName` | 否 | 函数名称模糊搜索 |
| `type` | 否 | 传 `EXTERNAL` 只看外部 API 算子 |
| `publishStatus` | 否 | `0` 未发布 / `1` 已发布；不传都返回 |
| `startDate` / `endDate` | 否 | 创建日期 `yyyy-MM-dd` |
| `pageNum` / `pageSize` | 否 | 默认 1 / 10 |

---

## 6. 查询详情 `GET {BASE}/function/detail`
```
GET {BASE}/function/detail?functionApi=ext.httpbin.get&version=1.0.0
```
- `functionApi` 必填；`version` **必填**（格式 x.y.z）。
- EXTERNAL 类型返回 `apiConfig`（解析后的接口定义）+ `params`（出入参列表，供前端渲染端口）。

---

## 7. 测试执行 `POST {BASE}/function/test`

> 后端按函数 `type` 自动分发；EXTERNAL 走 `ExternalApiOperator` → `ExternalApiExecutor`（OkHttp）发起真实 HTTP 调用。

### 7.1 GET + query 参数（对应 §1.1）
```json
{
  "functionApi": "ext.httpbin.get",
  "version": "1.0.0",
  "parameters": [
    { "paramName": "name", "paramValue": "foundry" }
  ]
}
```
**预期返回**：
```json
{
  "code": 200,
  "data": {
    "data": {
      "origin": "xxx.xxx.xxx.xxx",
      "url": "https://httpbin.org/get?name=foundry"
    },
    "description": "函数算子执行结果"
  }
}
```

### 7.2 POST + body 参数（对应 §1.2）
```json
{
  "functionApi": "ext.httpbin.post",
  "version": "1.0.0",
  "parameters": [
    { "paramName": "title", "paramValue": "hello" },
    { "paramName": "content", "paramValue": "world" }
  ]
}
```
**预期返回**：
```json
{
  "code": 200,
  "data": {
    "data": {
      "echo_title": "hello",
      "echo_content": "world"
    },
    "description": "函数算子执行结果"
  }
}
```

### 7.3 路径占位符（对应 §1.3）
```json
{
  "functionApi": "ext.httpbin.path",
  "version": "1.0.0",
  "parameters": [
    { "paramName": "userId", "paramValue": "u123" },
    { "paramName": "orderId", "paramValue": "o456" }
  ]
}
```
**预期返回**：
```json
{
  "code": 200,
  "data": {
    "data": {
      "method": "GET",
      "request_url": "https://httpbin.org/anything/u123/orders/o456"
    },
    "description": "函数算子执行结果"
  }
}
```

### 7.4 API-Key 鉴权（对应 §1.4）
```json
{
  "functionApi": "ext.httpbin.apikey",
  "version": "1.0.0",
  "parameters": []
}
```
**预期返回**（httpbin 原样返回请求头）：
```json
{
  "code": 200,
  "data": {
    "data": {
      "api_key_header": "sk-test-abc123"
    },
    "description": "函数算子执行结果"
  }
}
```

### 7.5 字面量映射（对应 §1.5）
```json
{
  "functionApi": "ext.httpbin.literal",
  "version": "1.0.0",
  "parameters": [
    { "paramName": "traceId", "paramValue": "trace-789" }
  ]
}
```
**预期返回**：
```json
{
  "code": 200,
  "data": {
    "data": {
      "accept_header": "application/json",
      "trace_header": "trace-789"
    },
    "description": "函数算子执行结果"
  }
}
```

---

## 8. 正式执行 `POST {BASE}/function/execute`

> 与 `/test` 使用相同参数结构，区别在于 `/execute` 是正式调用链路。

```json
{
  "functionApi": "ext.httpbin.get",
  "version": "1.0.0",
  "parameters": [
    { "paramName": "name", "paramValue": "foundry-execute" }
  ]
}
```

---

## 9. 故障排查

| 现象 | 可能原因 | 解决 |
|------|---------|------|
| `外部API算子配置为空` | 创建时 apiConfig 未传或为空 | 确保 apiConfig 非空 |
| `外部API算子必须提供接口地址 endpoint` | endpoint 字段缺失 | 补上 endpoint |
| `外部API地址非法` | endpoint 不是合法 URL | 检查 http/https 前缀 |
| `外部API调用失败（ConnectException）` | 目标 API 不可达 | 检查网络 / httpbin.org 是否可访问 |
| `外部API地址缺少路径参数：xxx` | endpoint 有 `{xxx}` 但 parameters 没传对应值 | 补上缺失的参数 |
| `外部API请求映射位置非法` | location 不是 path/query/header/body 之一 | 检查映射配置 |
| `响应不是合法 JSON，无法按映射取值` | 目标返回了非 JSON 响应 | 检查 responseMappings 是否匹配实际响应格式 |
| `外部API配置了 api-key 鉴权但未填写密钥` | authType=api-key 但 apiKey 为空 | 补上 apiKey |
| 401 未认证 | 鉴权拦截器生效 | 加 `access-token: Bearer xxx` 请求头 |

---

## 10. 建议测试顺序（自测清单）
1. **创建 GET 算子**（§1.1）→ 查询详情（§6）确认出入参已自动生成。
2. **测试 GET**（§7.1）→ 验证 query 参数拼接与响应映射。
3. **创建 POST 算子**（§1.2）→ 测试 POST（§7.2）→ 验证 body 参数与嵌套响应映射。
4. **创建路径参数算子**（§1.3）→ 测试（§7.3）→ 验证 `{xxx}` 占位符替换。
5. **创建 API-Key 算子**（§1.4）→ 测试（§7.4）→ 验证认证头注入。
6. **创建字面量算子**（§1.5）→ 测试（§7.5）→ 验证 literal 固定值不走入参。
7. **创建自动推导算子**（§1.6）→ 查询详情 → 确认出入参从映射自动推导。
8. **更新**（§2.1）→ 详情确认参数已重建。
9. **发布 → 修改**（§3.1 → §2.1）→ 应报"请先下线"。
10. **下线 → 删除**（§3.2 → §4）→ 成功。
