# 脚本参数解析接口测试用例（/parse_params，ApiPost 直接抄）

后端 AST 静态解析脚本 handle 的输入/输出参数：**Java/Groovy 在主应用进程内解析，Python/TypeScript 转发到各自 runner 的 `/parse`**。

- **服务器 IP 请自行替换**：主应用 `172.16.18.62`（ontology-server），runner `172.16.18.58`（`application-local.yml` 里 `script-runner.*` 已配）。
- **主应用端口/前缀**：`37002`，context-path `/ontology`。
- **本接口不执行脚本、不建图、无副作用**，只读源码做静态解析，安全可反复调用。

---

## 一、接口定义

- Method: `POST`
- URL: `http://172.16.18.62:37002/ontology/function/parse_params`
- Header:
  - `Content-Type: application/json`
  - `Authorization: Bearer <access-token>`（按当前网关/鉴权配置，若接口在白名单内可省略；token 用登录接口获取，必须带 `Bearer ` 前缀）
- Body:
```json
{
  "language": "GROOVY",
  "code": "def handle(input) { return [greeting: 'hi ' + input.name] }"
}
```

### 请求字段
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `language` | String | 是 | 枚举名：`GROOVY` / `JAVA` / `PYTHON` / `TYPESCRIPT`（大写） |
| `code` | String | 是 | 脚本源码，非空，长度上限 200000 字符 |

### 响应结构 `data`
```json
{
  "status": "OK",
  "parsed": true,
  "parameters": [
    { "paramName": "name", "paramType": "OBJECT", "category": "INPUT", "paramOrder": 1, "referenceType": "", "paramSchema": null, "description": null },
    { "paramName": "greeting", "paramType": "OBJECT", "category": "OUTPUT", "paramOrder": 1, "referenceType": "", "paramSchema": null, "description": null }
  ],
  "message": null
}
```

> `parameters` 为单个扁平数组（入参在前、出参在后，靠 `category` 区分），字段与保存接口 `FunctionParamDTO` 完全一致，可直接放进创建/更新请求提交。

| 字段 | 说明 |
|------|------|
| `status` | 解析状态：`OK`/`SYNTAX_ERROR`/`NO_HANDLE`/`RUNNER_NOT_CONFIGURED`/`RUNNER_ERROR`/`PARSE_EXCEPTION` |
| `parsed` | 是否成功解析出参数（`status=OK` 且至少有一个参数为 true）；false 时前端回退手动声明 |
| `parameters` | 解析出的参数列表；`paramOrder` 同 category 内从 1 自增 |
| `paramType` | 归一化枚举：`STRING`/`INTEGER`/`LONG`/`FLOAT`/`DOUBLE`/`BOOL`/`BYTE`/`SHORT`/`CHAR`/`LIST`/`DATE`/`OBJECT` |
| `referenceType` | 全限定类名：**Java/Groovy** 按声明类型自动填充（`String`→`java.lang.String`、`int`→`java.lang.Integer`、`List<T>` 保留泛型、启发推断项兜底 `java.lang.Object`），执行链路用它做类型转换；**Python/TS** 固定为 `""`（无 Java 类概念，执行时按原始 JSON 透传） |
| `paramSchema` | 静态解析不产出，为 null（与前端保存请求一致：本来就不传此字段） |
| `description` | 静态解析不产出，为 null（业务语义由用户保存前自行填写） |
| `message` | 失败/dynamic 时的提示；成功解析出参数时为 null |

> 解析可靠性分两档：**类型签名可解**（referenceType 按声明推导）与**启发推断**（paramType=OBJECT、referenceType 兜底 java.lang.Object，供参考）；最差为 `parsed=false`，手动补。

---

## 二、Groovy（进程内解析）

### 2.1 类型签名 → 展开类字段（ANNOTATION）
- Body:
```json
{
  "language": "GROOVY",
  "code": "class ExecuteInput { String name; Number count; Boolean enabled }\nclass ExecuteOutput { String message; Number score; Boolean ok }\nExecuteOutput handle(ExecuteInput input) { return new ExecuteOutput(message: input.name) }"
}
```
- 期望：`status=OK`，`parsed=true`
  - INPUT：`name(STRING)` / `count(DOUBLE)` / `enabled(BOOL)`，referenceType 按声明推导（如 java.lang.String）
  - OUTPUT：`message(STRING)` / `score(DOUBLE)` / `ok(BOOL)`，referenceType 按声明推导

### 2.2 无类型 handle → 函数体启发（INFERRED）
- Body:
```json
{
  "language": "GROOVY",
  "code": "def handle(input) { return [greeting: 'hi ' + input.name, ok: true] }"
}
```
- 期望：INPUT `name(OBJECT,INFERRED)`；OUTPUT `greeting(OBJECT,INFERRED)`、`ok(OBJECT,INFERRED)`

### 2.3 隐式返回（末表达式即返回值，无 return 关键字）★本轮修复点
- Body:
```json
{
  "language": "GROOVY",
  "code": "def handle(input) { [greeting: 'hi ' + input.name, total: input.count] }"
}
```
- 期望：OUTPUT 含 `greeting`、`total`（INFERRED）；INPUT 含 `name`、`count`
- 说明：修复前 Groovy 这种写法解析结果为空，现能正确采集。

---

## 三、Java（进程内解析，JavaParser 纯语法）

### 3.1 类型签名 → 展开字段 + 忽略 static 常量 ★本轮修复点
- Body:
```json
{
  "language": "JAVA",
  "code": "class Input { String name; int count; }\nclass Output { String message; boolean ok; }\npublic class Main { private static final long serialVersionUID = 1L; Output handle(Input in) { return new Output(); } }"
}
```
- 期望：INPUT `name(STRING)`、`count(INTEGER)`；OUTPUT `message(STRING)`、`ok(BOOL)`；**`serialVersionUID` 不出现在结果里**（static 字段已过滤）

### 3.2 Map 入参 → `get("key")` 字面量推断（INFERRED）
- Body:
```json
{
  "language": "JAVA",
  "code": "import java.util.Map;\npublic class H {\n  Map<String,Object> handle(Map<String,Object> in) {\n    Object a = in.get(\"alpha\");\n    return null;\n  }\n}"
}
```
- 期望：INPUT 含 `alpha(OBJECT,INFERRED)`

---

## 四、Python（走 runner `/parse`，端口 39101）

### 4.1 类型注解类 → 展开字段（ANNOTATION）
- Body:
```json
{
  "language": "PYTHON",
  "code": "class In:\n    name: str\n    age: int\nclass Out:\n    msg: str\n    ok: bool\ndef handle(i: In) -> Out:\n    return Out()\n"
}
```
- 期望：INPUT `name(STRING)`、`age(INTEGER)`；OUTPUT `msg(STRING)`、`ok(BOOL)`（类型签名可解）

### 4.2 `__init__` 里 `self.x` 赋值字段 → 展开 ★本轮修复点
- Body:
```json
{
  "language": "PYTHON",
  "code": "class In:\n    def __init__(self):\n        self.name = ''\n        self.count = 0\ndef handle(i: In):\n    return {'total': i.count}\n"
}
```
- 期望：INPUT `name(OBJECT,ANNOTATION)`、`count(OBJECT,ANNOTATION)`（无显式注解类型→OBJECT）；OUTPUT `total(OBJECT,INFERRED)`
- 说明：修复前仅识别类级 `x: type` 注解，`__init__` 里 `self.x=` 会静默解析为 0 个入参，现已支持。

### 4.3 无类型 → `input["k"]` / `input.attr` 启发（INFERRED）
- Body:
```json
{
  "language": "PYTHON",
  "code": "def handle(input):\n    return {'greeting': input['name']}\n"
}
```
- 期望：INPUT `name(OBJECT,INFERRED)`；OUTPUT `greeting(OBJECT,INFERRED)`

---

## 五、TypeScript（走 runner `/parse`，端口 39102）

### 5.1 interface → 展开字段（ANNOTATION）
- Body:
```json
{
  "language": "TYPESCRIPT",
  "code": "interface In { name: string; count: number }\ninterface Out { msg: string; ok: boolean }\nfunction handle(i: In): Out { return { msg: i.name, ok: true }; }"
}
```
- 期望：INPUT `name(STRING)`、`count(DOUBLE)`；OUTPUT `msg(STRING)`、`ok(BOOL)`（类型签名可解）

### 5.2 守卫分支 + 多 return → 键并集 ★本轮修复点
- Body:
```json
{
  "language": "TYPESCRIPT",
  "code": "function handle(input: any) { if (!input) { return { err: 'no' }; } return { data: 'x', name: 'a' }; }"
}
```
- 期望：OUTPUT 同时含 `err(STRING)`、`data(STRING)`、`name(STRING)`（INFERRED）
- 说明：修复前只取第一个 return，会把守卫分支的 `err` 当成唯一出参；现收集全部 return 键并集。

### 5.3 `const handle = 箭头函数` + 下标访问 ★本轮修复点
- Body:
```json
{
  "language": "TYPESCRIPT",
  "code": "const handle = (input: any): any => { const v = input['userId']; return { ok: true, total: 1 }; };"
}
```
- 期望：INPUT `userId(OBJECT,INFERRED)`（`input['userId']` 下标可识别）；OUTPUT `ok(BOOL)`、`total(DOUBLE)`
- 说明：修复前 `findHandle` 只认 `function handle`，`const handle = () => {}` 解析不到；现支持箭头/函数表达式写法。

---

## 六、失败/边界状态（验证不再静默吞异常）

### 6.1 语法错误 → SYNTAX_ERROR
- Body（Groovy，故意缺括号）:
```json
{
  "language": "GROOVY",
  "code": "def handle(input { return [a: 1 }"
}
```
- 期望：`status=SYNTAX_ERROR`，`parsed=false`，`message` 含语法错误信息

### 6.2 缺 handle 入口 → NO_HANDLE
- Body（Groovy，只有语句无 handle）:
```json
{
  "language": "GROOVY",
  "code": "println 'hello'"
}
```
- 期望：`status=NO_HANDLE`，`parsed=false`

### 6.3 完全动态写法 → OK 但 parsed=false
- Body（Python，返回值来自未知函数调用）:
```json
{
  "language": "PYTHON",
  "code": "def handle(input):\n    return compute(input)\n"
}
```
- 期望：`status=OK`，`parsed=false`，`message` 提示"完全动态写法，请手动声明"

### 6.4（可选）runner 不可用 → RUNNER_ERROR / RUNNER_NOT_CONFIGURED
- 把 python/ts runner 容器停掉，再发一条 `PYTHON`/`TYPESCRIPT` 解析请求。
- 期望：`status=RUNNER_ERROR`（连不上）或 base-url 未配时 `RUNNER_NOT_CONFIGURED`；Java/Groovy 不受影响（进程内）。
- 恢复：`docker compose start python-runner ts-runner`。

---

## 附录 A：curl 冒烟（Linux 服务器直接跑）
```bash
# Groovy（进程内）
curl -s -X POST http://172.16.18.62:37002/ontology/function/parse_params \
  -H "Content-Type: application/json" \
  -d '{"language":"GROOVY","code":"def handle(input) { return [greeting: 1 + input.name] }"}'

# 直连 runner /parse（绕开主应用，单测 Python/TS 侧）
curl -s -X POST http://172.16.18.58:39101/parse -H "Content-Type: application/json" \
  -d '{"code":"def handle(input):\n    return {\"ok\": True}"}'
curl -s -X POST http://172.16.18.58:39102/parse -H "Content-Type: application/json" \
  -d '{"code":"function handle(input){ return {ok:true}; }"}'
```
直连 runner 期望：`{"ok":true,"params":[{"name":"ok","type":"boolean","category":"OUTPUT","source":"INFERRED"}]}`

## 附录 B：快速排查
| 现象 | 可能原因 |
|------|---------|
| 全部 404 | context-path/端口不对；主应用路径应为 `/ontology/function/parse_params` |
| Python/TS 返回 `RUNNER_NOT_CONFIGURED` | 主应用 `application-local.yml` 未配 `script-runner.python/typescript` |
| Python/TS 返回 `RUNNER_ERROR: 调用...失败` | runner 容器没起 / IP端口不通 / 还在 `pip install`·`npm install` |
| 直连 `/parse` 正常，主应用报 `RUNNER_ERROR` | 主应用配的 runner IP 与实际不符 |
| Java/Groovy 有结果但 Python/TS 空 | runner 侧 `/parse` 代码未更新（需传 `executor.py`/`app.py`/`server.js` 并 restart） |
| `paramType` 与预期不符 | INFERRED 项统一为 OBJECT 属正常（启发层不推断具体类型） |
