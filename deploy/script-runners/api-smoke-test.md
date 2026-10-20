# Runner 冒烟测试用例（ApiPost 直接抄）

**服务器 IP 请自行替换 `172.16.18.62`**（服务器执行 `hostname -I` 查询）

端口：Python=39101 / TS=39102

> Groovy / Java 已在 ontology-server 主程序进程内执行，无独立容器，不在此文列测试范围；
> 可通过主应用接口 `POST /ontology/function/test`（language=GROOVY/JAVA）验证。

---

## 一、Python Runner

### 1.1 健康检查
- Method: `GET`
- URL: `http://172.16.18.62:39101/health`
- 期望响应: `{"status":"UP","language":"python"}`

### 1.2 静态校验
- Method: `POST`
- URL: `http://172.16.18.62:39101/validate`
- Header: `Content-Type: application/json`
- Body:
```json
{
  "code": "def handle(input):\n    return {\"data\": input[\"x\"]}\n"
}
```
- 期望响应: `{"ok":true}`

### 1.3 最小 handle 执行
- Method: `POST`
- URL: `http://172.16.18.62:39101/functions/handle`
- Header: `Content-Type: application/json`
- Body:
```json
{
  "functionApi": "smoke.python",
  "version": "1.0.0",
  "code": "def handle(input):\n    return {\n        \"data\": \"hello \" + input[\"name\"],\n        \"description\": \"api smoke\",\n        \"status\": \"SUCCESS\"\n    }\n",
  "input": { "name": "python" }
}
```
- 期望响应:
```json
{
  "data": "hello python",
  "description": "api smoke",
  "status": "SUCCESS",
  "_meta": { "durationMs": 15, "status": "SUCCESS", "language": "python" }
}
```

---

## 二、TypeScript Runner

### 2.1 健康检查
- Method: `GET`
- URL: `http://172.16.18.62:39102/health`
- 期望响应: `{"status":"UP","language":"typescript"}`

### 2.2 静态校验
- Method: `POST`
- URL: `http://172.16.18.62:39102/validate`
- Header: `Content-Type: application/json`
- Body:
```json
{
  "code": "function handle(input: any): any { return { data: input.x }; }"
}
```
- 期望响应: `{"ok":true}`

### 2.3 最小 handle 执行
- Method: `POST`
- URL: `http://172.16.18.62:39102/functions/handle`
- Header: `Content-Type: application/json`
- Body:
```json
{
  "functionApi": "smoke.ts",
  "version": "1.0.0",
  "code": "function handle(input: any): any {\n    return {\n        data: 'hello ' + input.name,\n        description: 'api smoke',\n        status: 'SUCCESS'\n    };\n}",
  "input": { "name": "typescript" }
}
```
- 期望响应:
```json
{
  "data": "hello typescript",
  "description": "api smoke",
  "status": "SUCCESS",
  "_meta": { "durationMs": 20, "status": "SUCCESS", "language": "typescript" }
}
```

---

## 推荐执行顺序

1. **先测 2 个 `/health`**（一秒钟出结果）→ 判断容器活着 + 网络通
2. **Python / TS 依次冒烟**（validate → handle）→ 判断契约与解析没问题
3. **Groovy / Java 走主应用接口测试**：`POST /ontology/function/test`（见 api-full-flow-test.md）

---

## 快速排查

| 现象 | 可能原因 |
|------|---------|
| `ECONNREFUSED` | IP 不对 / 端口没 listen / 防火墙没放行 |
| `timeout` | 安全组没放开 / 走了错误的网卡 IP |
| /health 返回但 handle 报 `No message available` | Body 字段名或 JSON 格式错误（`functionApi` 是驼峰，不是 `function_api`） |
| Python/TS 首次请求 503 | 容器还在 `pip install` / `npm install`，等 60 秒再试 |
