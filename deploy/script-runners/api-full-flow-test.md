# 脚本函数完整链路测试（四语言，ApiPost 直抄）

覆盖 **Groovy / Java / Python / TypeScript** 四种脚本语言各自的完整链路：
创建 → 查详情 → 编辑 → 测试 → 发布 → 正式执行 → 清理，外加每种语言的负向用例。

> 下文所有 `172.16.18.58` 换成你的实际服务器 IP（服务器上 `hostname -I` 查询）。
> ontology-server：`http://172.16.18.58:37002`，context-path `/ontology`，故 **Base URL = `http://172.16.18.58:37002/ontology`**

---

## 0. 前置检查（一次性）

### 0.1 Python / TS runner 容器健康

| Runner | 端口 | 语言 | 健康检查 URL |
|--------|------|------|-------------|
| python-runner | 39101 | PYTHON | `http://172.16.18.58:39101/health` |
| ts-runner | 39102 | TYPESCRIPT | `http://172.16.18.58:39102/health` |

> Groovy / Java 已改为主程序进程内执行，**无需 groovy-runner / java-runner 容器**。

服务器上快速全检：
```bash
for p in 39101 39102; do echo -n "$p -> "; curl -s http://127.0.0.1:$p/health; echo; done
```
期望每行都是 `{"status":"UP","language":"..."}`。

### 0.2 ontology-server 的配置

1. `application-local.yml` 中 `script-runner.*` 必须是**服务器真实 IP**，不能是 `127.0.0.1`
（ontology-server 跑在容器里时，`127.0.0.1` 指向容器自身，会 `Failed to connect`）：

```yaml
script-runner:
  python: http://172.16.18.58:39101
  typescript: http://172.16.18.58:39102
  timeout-ms: 10000
```

2. **Java 脚本进程内动态编译需要 JDK**：ontology-server 必须用完整 JDK 17 启动（`eclipse-temurin:17-jdk`，
   不能是 `-jre`），否则会报 `当前运行时不是 JDK，无法编译 Java 脚本`。

### 0.3 登录拿 token

- Method: `POST`
- URL: `http://172.16.18.58:37002/ontology/user/login`
- Header: `Content-Type: application/json`
- Body:
```json
{ "username": "admin", "password": "a123456" }
```
- 取响应里的 `data.accessToken`（**值自带 `Bearer ` 前缀**）
- 之后**每个请求**都要带：`Authorization: Bearer eyJhbGciOi...`

---

## 1. 通用契约（四语言完全一致）

### 1.1 接口清单

| 动作 | Method | URL | 参数形式 |
|------|--------|-----|---------|
| 创建 | POST | `/ontology/function` | JSON body |
| 编辑 | PUT | `/ontology/function` | JSON body |
| 详情 | GET | `/ontology/function/detail` | query：`functionApi` + `version`（都必填） |
| 列表 | GET | `/ontology/function/list` | query：`ontologySpaceId`/`displayName`/`type`/`publishStatus`/`pageNum`/`pageSize` |
| 测试 | POST | `/ontology/function/test` | JSON body |
| 发布 | POST | `/ontology/function/publish` | query：`functionApi` + `version` |
| 下线 | POST | `/ontology/function/unpublish` | query：`functionApi` + `version` |
| 正式执行 | POST | `/ontology/function/execute` | JSON body |
| 删除 | DELETE | `/ontology/function/delete` | query：`functionApi` + `version` |

### 1.2 创建 body 字段

| 字段 | 必填 | 说明 |
|------|------|------|
| `functionApi` | ✅ | 全局唯一，创建后**不可改** |
| `version` | ✅ | 必须 `x.y.z`；同 api 下必须**大于**已有最大版本 |
| `displayName` | ✅ | 创建后不可改 |
| `type` | ✅ | 脚本算子固定 `CUSTOMIZE` |
| `language` | ❌ | `GROOVY`/`JAVA`/`PYTHON`/`TYPESCRIPT`；**仅 CUSTOMIZE 会落库**，创建后锁定（换语言请走新版本）；不传默认 `GROOVY` |
| `ontologySpaceId` | ❌ | 所属本体空间 id（本例用 `1`，按实际改） |
| `code` | ❌ | 脚本源码 |
| `parameters` | 见下 | 参数定义列表 |

**`parameters` 元素字段**（创建/编辑用，注意与测试接口不同）：
```json
{ "paramName": "name", "paramType": "STRING", "category": "INPUT", "paramOrder": 1, "description": "姓名" }
```
- `paramType` 取值：`STRING` `INTEGER` `LONG` `FLOAT` `DOUBLE` `BOOL` `BYTE` `SHORT` `CHAR` `LIST` `DATE` `OBJECT`
- `category`：`INPUT`（入参）/ `OUTPUT`（出参）
- **Python / TypeScript 必须显式传**（后端无法反射解析非 JVM 脚本）；Groovy 可不传（老契约脚本能反射，新契约 `handle(input)` 解析不出参数，只记 warn 不建参数）

### 1.3 脚本入口要求（各语言唯一差异点）

| 语言 | 入口写法 | 关键点 |
|------|---------|--------|
| Groovy | `def handle(input) { ... }` | 顶层脚本即可；返回 Map 或 `FunctionResultVO` |
| Java | `public class Fn { public Map<String,Object> handle(Map<String,Object> input) {...} }` | **必须有 class 定义**；`handle` 必须是**实例方法**（runner 会 `newInstance()`）；需要无参构造；类名取代码里第一个 class |
| Python | `def handle(input): ...` | 顶层必须有 `def handle`（AST 静态检查，没有就判"函数 handle 方法不存在"） |
| TypeScript | `function handle(input: any): any { ... }` | 顶层声明 `function handle` 即可（无需 `export`）；只允许 `require` Node 内置模块 |

**返回值统一约定**：返回一个对象/Map，字段按需取
- `data`：业务结果（主应用与前端只对这个字段负责后续业务处理）
- `description`：描述
- `taskId`：异步任务号（有值时主应用会落一条待处理任务）
- `startTime` / `endTime` / `timeWindows`：可见窗口类算子用
- 其他自定义键（如 `status`）**允许透传**，主应用不再因未知字段报错
- 返回裸值（字符串/数字）会被包成 `{"data": 裸值}`

---

## 2. Groovy 完整链路

### 2.1 创建

- POST `http://172.16.18.58:37002/ontology/function`
- Headers：`Content-Type: application/json`、`Authorization: <accessToken>`
- Body:
```json
{
  "functionApi": "smoke.hello",
  
  
  
  "version": "1.0.0",
  "displayName": "冒烟测试函数-Groovy",
  "description": "验证脚本函数完整链路",
  "type": "CUSTOMIZE",
  "language": "GROOVY",
  "ontologySpaceId": 1,
  "code": "def handle(input) {\n    def name = input.name ?: 'world'\n    return [data: 'hello ' + name, description: 'from groovy runner', status: 'SUCCESS']\n}",
  "parameters": [
    { "paramName": "name", "paramType": "STRING", "category": "INPUT",  "paramOrder": 1, "description": "姓名" },
    { "paramName": "data", "paramType": "STRING", "category": "OUTPUT", "paramOrder": 1, "description": "问候语" }
  ]
}
```
- 期望：`{"code":200,"message":"success"}`
- 背后动作：先进程内编译校验（Groovy 在主 JVM 内编译探测 handle）→ 通过后落库（`publish=0`）→ 写 `function_param`
- 若提示 `函数版本已存在:smoke.hello 1.0.0`：先执行 §2.7 清理；若要保留旧版本则把 `version` 改成 `1.0.1`（必须大于已有最大版本）

### 2.2 查详情

- GET `http://172.16.18.58:37002/ontology/function/detail?functionApi=smoke.hello&version=1.0.0`
- Header：`Authorization: <accessToken>`
- 校验点：`data.language == "GROOVY"`、`data.publish == 0`、`data.code` 是刚提交的脚本、入参/出参列表非空

### 2.3 编辑（换代码 + 换描述）

- PUT `http://172.16.18.58:37002/ontology/function`
- Body:
```json
{
  "functionApi": "smoke.hello",
  "version": "1.0.0",
  "description": "编辑后的描述",
  "code": "def handle(input) {\n    def name = input.name ?: 'world'\n    return [data: 'HI ' + name.toString().toUpperCase(), description: 'edited', status: 'SUCCESS']\n}"
}
```
- 期望：`{"code":200,"message":"success"}`
- **要点**：只允许改未发布版本；`functionApi`/`version`/`language`/`displayName` 不可改；
  不传 `parameters` 时**保留原有参数定义**（新契约脚本反射解析不出参数，后端不再误删）；想改参数就整列表一起传

### 2.4 测试（走完整执行链路）

- POST `http://172.16.18.58:37002/ontology/function/test`
- Body（**只认这三个字段**）:
```json
{
  "functionApi": "smoke.hello",
  "version": "1.0.0",
  "parameters": [ { "paramName": "name", "paramValue": "foundry" } ]
}
```
- 期望：
```json
{ "code": 200, "data": { "data": "HI FOUNDRY", "description": "edited", "status": "SUCCESS" } }
```
- 校验点：
  1. `data` 是**结构化对象**不是 JSON 字符串（`testFunction` 统一返回的修复点）
  2. `data.data == "HI FOUNDRY"` → 执行的是**编辑后**的代码且缓存已失效
  3. 多出的 `status` 键不报错（结果解析放宽的修复点）
  4. `parameters` 传空数组 `[]` → 期望 `"data": "HI WORLD"`（后端不下发空值键，脚本默认值生效）

> ⚠️ 别把创建/编辑的 body（`code`/`language`/`ontologySpaceId`…）发到 `/test`，多余字段会被忽略，
> 且 `parameters` 元素字段名是 `paramValue`（不是 `paramType`）。

### 2.5 发布

- POST `http://172.16.18.58:37002/ontology/function/publish?functionApi=smoke.hello&version=1.0.0`
- 期望：`{"code":200,"message":"success"}`；发布后 `publish=1`，再 PUT 会报"已发布的函数不能修改，请先下线"

### 2.6 正式执行

- POST `http://172.16.18.58:37002/ontology/function/execute`
- Body:
```json
{
  "functionApi": "smoke.hello",
  "version": "1.0.0",
  "parameters": [ { "paramName": "name", "paramValue": "prod" } ]
}
```
- 期望：`{"code":200,"data":"{\"data\":\"HI PROD\",\"description\":\"edited\",\"status\":\"SUCCESS\"}"}`
- ⚠️ `/execute` 返回的是**JSON 字符串**（老契约，业务侧自己解析），与 `/test` 返回对象是两条路径，都正常
- 说明：`executeFunction` 当前**不校验发布状态**（发布位只影响前端可用性），发布前后都能调通

### 2.7 下线 + 删除

```
POST   /ontology/function/unpublish?functionApi=smoke.hello&version=1.0.0
DELETE /ontology/function/delete?functionApi=smoke.hello&version=1.0.0
```
（已发布版本必须先下线才能删，报 `已发布的函数不能删除，请先下线`；被本体行为关联的函数也不能删/下线）

---

## 3. Java 完整链路

结构与 Groovy 完全一致，只有 `functionApi`、`language`、`code` 三处不同。

### 3.1 创建

- POST `http://172.16.18.58:37002/ontology/function`
```json
{
  "functionApi": "smoke.hello.java",
  "version": "1.0.0",
  "displayName": "冒烟测试函数-Java",
  "description": "Java 脚本链路验证",
  "type": "CUSTOMIZE",
  "language": "JAVA",
  "ontologySpaceId": 1,
  "code": "public class Fn {\n    public java.util.Map<String,Object> handle(java.util.Map<String,Object> input) {\n        Object n = input == null ? null : input.get(\"name\");\n        String name = (n == null || String.valueOf(n).isEmpty()) ? \"world\" : String.valueOf(n);\n        java.util.Map<String,Object> r = new java.util.HashMap<>();\n        r.put(\"data\", \"hello \" + name);\n        r.put(\"description\", \"from java runner\");\n        r.put(\"status\", \"SUCCESS\");\n        return r;\n    }\n}",
  "parameters": [
    { "paramName": "name", "paramType": "STRING", "category": "INPUT",  "paramOrder": 1, "description": "姓名" },
    { "paramName": "data", "paramType": "STRING", "category": "OUTPUT", "paramOrder": 1, "description": "问候语" }
  ]
}
```
- 期望：`{"code":200,"message":"success"}`
- **Java 独有前置**：ontology-server 主程序必须用 **JDK 17** 启动（进程内动态编译依赖 javac），只有 JRE 会报 `当前运行时不是 JDK，无法编译 Java 脚本`；Java 脚本里可直接 `import com.aircas.ptr.foundry.*` 引用主程序内部类

### 3.2 详情

- GET `/ontology/function/detail?functionApi=smoke.hello.java&version=1.0.0`
- 校验点：`language == "JAVA"`

### 3.3 编辑

```json
{
  "functionApi": "smoke.hello.java",
  "version": "1.0.0",
  "description": "编辑后的描述",
  "code": "public class Fn {\n    public java.util.Map<String,Object> handle(java.util.Map<String,Object> input) {\n        Object n = input == null ? null : input.get(\"name\");\n        String name = (n == null || String.valueOf(n).isEmpty()) ? \"world\" : String.valueOf(n);\n        java.util.Map<String,Object> r = new java.util.HashMap<>();\n        r.put(\"data\", \"HI \" + name.toUpperCase());\n        r.put(\"description\", \"edited\");\n        r.put(\"status\", \"SUCCESS\");\n        return r;\n    }\n}"
}
```

### 3.4 测试

```json
{
  "functionApi": "smoke.hello.java",
  "version": "1.0.0",
  "parameters": [ { "paramName": "name", "paramValue": "foundry" } ]
}
```
- 期望：`{"code":200,"data":{"data":"HI FOUNDRY","description":"edited","status":"SUCCESS"}}`
- 空参数复测（`"parameters": []`）→ 期望 `"data":"HI WORLD"`

### 3.5 / 3.6 发布与执行

```
POST /ontology/function/publish?functionApi=smoke.hello.java&version=1.0.0
POST /ontology/function/execute   body: {"functionApi":"smoke.hello.java","version":"1.0.0","parameters":[{"paramName":"name","paramValue":"prod"}]}
```
- execute 期望：`data` 为 JSON 字符串，内含 `"data":"HI PROD"`

### 3.7 Java 脚本的三个高频坑

| 报错 | 原因 | 正确写法 |
|------|------|---------|
| `未找到 class 定义，Java 脚本须将 handle 包在类中` | 只写了裸方法 | 必须 `public class Fn { ... }` 包住 |
| `函数 handle 方法不存在` | `handle` 写成了 `static` | 改为**实例方法**（runner 先 `newInstance()` 再反射调用） |
| `Java 编译失败：...` | 语法错 / 缺 import | 全限定名写全（如 `java.util.Map`），或代码里带 `import` |

---

## 4. Python 完整链路

### 4.1 创建

- POST `http://172.16.18.58:37002/ontology/function`
```json
{
  "functionApi": "smoke.hello.py",
  "version": "1.0.0",
  "displayName": "冒烟测试函数-Python",
  "description": "Python 脚本链路验证",
  "type": "CUSTOMIZE",
  "language": "PYTHON",
  "ontologySpaceId": 1,
  "code": "def handle(input):\n    name = (input or {}).get('name') or 'world'\n    return {\n        'data': 'hello ' + str(name),\n        'description': 'from python runner',\n        'status': 'SUCCESS'\n    }\n",
  "parameters": [
    { "paramName": "name", "paramType": "STRING", "category": "INPUT",  "paramOrder": 1, "description": "姓名" },
    { "paramName": "data", "paramType": "STRING", "category": "OUTPUT", "paramOrder": 1, "description": "问候语" }
  ]
}
```
- 期望：`{"code":200,"message":"success"}`
- **Python 必须显式传 `parameters`**（不传则 `function_param` 为空，前端测试面板拿不到入参）
- **取值写法务必用 `or`**：`input.get('name') or 'world'`
  用 `input.get('name', 'world')` 时，若上游真下发了 `"name": null`，拿到的是 `None` 而不是默认值，
  接着 `'hello ' + None` 会抛 `TypeError`（这是实测踩过的坑，主应用侧已改为不下发空值键，脚本仍建议自带兜底）

### 4.2 详情

- GET `/ontology/function/detail?functionApi=smoke.hello.py&version=1.0.0`
- 校验点：`language == "PYTHON"`；入参列表含 `name`

### 4.3 编辑

```json
{
  "functionApi": "smoke.hello.py",
  "version": "1.0.0",
  "description": "编辑后的描述",
  "code": "def handle(input):\n    name = (input or {}).get('name') or 'world'\n    return {\n        'data': 'HI ' + str(name).upper(),\n        'description': 'edited',\n        'status': 'SUCCESS'\n    }\n"
}
```

### 4.4 测试

```json
{
  "functionApi": "smoke.hello.py",
  "version": "1.0.0",
  "parameters": [ { "paramName": "name", "paramValue": "foundry" } ]
}
```
- 期望：`{"code":200,"data":{"data":"HI FOUNDRY","description":"edited","status":"SUCCESS"}}`
- 空参数复测（`"parameters": []`）→ 期望 `"data":"HI WORLD"`，**不再报** `远程调用失败, code=400, body={"ok":false,"error":"TypeError: ..."}`

### 4.5 / 4.6 发布与执行

```
POST /ontology/function/publish?functionApi=smoke.hello.py&version=1.0.0
POST /ontology/function/execute   body: {"functionApi":"smoke.hello.py","version":"1.0.0","parameters":[{"paramName":"name","paramValue":"prod"}]}
```

### 4.7 Python 高频坑

| 报错 | 原因 |
|------|------|
| `Python 语法错误：...（line N）` | 缩进/冒号问题，脚本没写成合法 Python 模块 |
| `函数 handle 方法不存在` | 没有顶层 `def handle`（函数名必须是 `handle`，且顶层定义，不能只在 class 里） |
| `脚本执行超时（>5000ms）` | 死循环/慢 IO；容器 `RUNNER_TIMEOUT_MS=5000`，子进程会被 terminate |
| `TypeError: can only concatenate str (not "NoneType")` | 用 `input.get(k, default)` 兜底 null，改 `input.get(k) or default` |

---

## 5. TypeScript 完整链路

### 5.1 创建

- POST `http://172.16.18.58:37002/ontology/function`
```json
{
  "functionApi": "smoke.hello.ts",
  "version": "1.0.0",
  "displayName": "冒烟测试函数-TS",
  "description": "TypeScript 脚本链路验证",
  "type": "CUSTOMIZE",
  "language": "TYPESCRIPT",
  "ontologySpaceId": 1,
  "code": "function handle(input: any): any {\n    const name = (input && input.name) ? input.name : 'world';\n    return {\n        data: 'hello ' + String(name),\n        description: 'from ts runner',\n        status: 'SUCCESS'\n    };\n}",
  "parameters": [
    { "paramName": "name", "paramType": "STRING", "category": "INPUT",  "paramOrder": 1, "description": "姓名" },
    { "paramName": "data", "paramType": "STRING", "category": "OUTPUT", "paramOrder": 1, "description": "问候语" }
  ]
}
```
- 期望：`{"code":200,"message":"success"}`

### 5.2 详情

- GET `/ontology/function/detail?functionApi=smoke.hello.ts&version=1.0.0`
- 校验点：`language == "TYPESCRIPT"`

### 5.3 编辑

```json
{
  "functionApi": "smoke.hello.ts",
  "version": "1.0.0",
  "description": "编辑后的描述",
  "code": "function handle(input: any): any {\n    const name = (input && input.name) ? input.name : 'world';\n    return {\n        data: 'HI ' + String(name).toUpperCase(),\n        description: 'edited',\n        status: 'SUCCESS'\n    };\n}"
}
```

### 5.4 测试

```json
{
  "functionApi": "smoke.hello.ts",
  "version": "1.0.0",
  "parameters": [ { "paramName": "name", "paramValue": "foundry" } ]
}
```
- 期望：`{"code":200,"data":{"data":"HI FOUNDRY","description":"edited","status":"SUCCESS"}}`
- 空参数复测 → `"data":"HI WORLD"`

### 5.5 / 5.6 发布与执行

```
POST /ontology/function/publish?functionApi=smoke.hello.ts&version=1.0.0
POST /ontology/function/execute   body: {"functionApi":"smoke.hello.ts","version":"1.0.0","parameters":[{"paramName":"name","paramValue":"prod"}]}
```

### 5.7 TypeScript 高频坑

| 报错 | 原因 |
|------|------|
| `TypeScript 语法错误：...` | 语法错（只做转译期检查，不做类型检查，所以类型写错不会拦） |
| `函数 handle 方法不存在` | 没声明顶层 `function handle` / `const handle` |
| `禁止 require 非内置模块：xxx` | 沙箱只允许 Node 内置模块，`require('axios')` 之类一律拒绝 |
| `脚本执行超时（>5000ms）` | 同步死循环；async handle 也有 `Promise.race` 超时 |

---

## 6. 四语言通用负向用例（每种语言都建议跑一遍）

| 编号 | 用例 | 操作 | 期望 |
|------|------|------|------|
| N1 | 语法错误脚本 | 创建时 `code` 传 `"def handle(input) { +"`（或各语言对应坏代码） | 创建**失败**，错误信息来自对应 runner 的 `/validate`（`Groovy 编译失败：` / `Java 编译失败：` / `Python 语法错误：` / `TypeScript 语法错误：`），数据库无脏数据 |
| N2 | 没有 handle | `code` 传 `"def foo(): pass"` 之类 | `函数 handle 方法不存在`，创建被拒 |
| N3 | 运行期异常 | 脚本里 `1/0` / `input['nope']`，然后 `/test` | 500，message 形如 `远程调用失败, code=400, body={"ok":false,"error":"..."}`，**错误原文来自 runner** |
| N4 | 参数缺省 | `/test` body `"parameters": []` | 走脚本自身默认值，返回 `HI WORLD`（本次修复点：不再下发 null 值键） |
| N5 | 编辑已发布 | publish 后再 PUT | `已发布的函数不能修改，请先下线：xxx` |
| N6 | 重复/非递增版本 | 同 `functionApi` 重复 `version` 再创建 | `函数版本已存在:xxx 1.0.0`；版本不比已有最大值大则 `新版本号必须大于当前最大版本号 1.0.0` |
| N7 | 超时 | 脚本 `while True: pass`（Python） | `脚本执行超时（>5000ms）`，runner 容器不被拖垮 |
| N8 | 语言不可改 | PUT body 里传 `language`（与创建时不同） | 字段不存在于更新契约，传了也无影响；DB 中 `language` 不变（PO 上 `updateStrategy=NEVER`），换语言请创建新版本 |

---

## 7. 完整链路一图

```
POST /user/login                              ← 拿 token（自带 Bearer 前缀）
    ↓
POST /function                                ← 创建：先远程 /validate 再落库（publish=0）
    ↓                                             language 决定走哪个 runner
GET  /function/detail?functionApi&version     ← 确认落库 + 参数定义
    ↓
PUT  /function                                ← 编辑（仅未发布；参数解析不出时保留原定义）
    ↓                                             并使 runner 编译缓存失效
POST /function/test                           ← ontology-server → ScriptEngineRouter
    ↓                                             → RemoteRunnerScriptEngine → runner /functions/handle
POST /function/publish?functionApi&version    ← publish=1
    ↓
POST /function/execute                        ← 同一条 runner 通路，返回 JSON 字符串
    ↓
POST /function/unpublish + DELETE /function/delete   ← 清理
```

**test 与 execute 的差异**（都正常）：

| | `/test` | `/execute` |
|--|--------|-----------|
| 发布状态 | 允许未发布 | 当前实现不校验 |
| 返回 `data` | 结构化对象（`Map`） | JSON **字符串** |
| 用途 | IDE 试跑 | 业务/行为链路调用 |

---

## 8. 排错速查

| 现象 | 定位 | 处置 |
|------|------|------|
| `401 Unauthorized` | 没带 / 过期 token | 重登，检查 `Authorization` 头 |
| `Failed to connect to /127.0.0.1:39101` | `script-runner.python/typescript` 还是 `127.0.0.1` | 改成服务器真实 IP 并重启 ontology-server（仅影响 Python/TS，Groovy/Java 不走容器） |
| `当前运行时不是 JDK，无法编译 Java 脚本` | ontology-server 启动用了 JRE | 主程序镜像/启动 JVM 换成完整 JDK 17（进程内动态编译依赖 javac） |
| 远程调用失败, code=400, body=`{"ok":false,"error":"..."}` | 脚本自身编译/运行错误 | 按 `error` 原文改脚本；上服务器 `docker-compose logs python-runner`（或对应语言）看堆栈 |
| 创建成功但 `/test` 拿不到入参 | 非 JVM 语言没传 `parameters` | PUT 时带上完整 `parameters` 列表重建 |
| `/test` 的 `data` 是字符串 | ontology-server 没重启，修复未生效 | 重新打包 + 上传 jar + `docker-compose restart` |
| 500 且日志 `UndeclaredThrowableException` + `Unrecognized field` | 旧 jar 严格反序列化 `FunctionResultVO` | 用最新 jar（已改为宽松解析） |
| `已发布函数不能修改/不能删除/不能下线` | publish=1 或被本体行为关联 | 先 unpublish；被关联需先解绑行为，或创建新版本 |
| python / ts 容器刚起 30-60s 内超时 | 冷启动在装 pip/npm 依赖 | 等 healthcheck 变 healthy 再测 |

---

## 9. 一键清理（四种语言全删）

```bash
BASE=http://172.16.18.58:37002/ontology
TOKEN='<accessToken>'
for api in smoke.hello smoke.hello.java smoke.hello.py smoke.hello.ts; do
  curl -s -X POST "$BASE/function/unpublish?functionApi=$api&version=1.0.0" -H "Authorization: $TOKEN"
  curl -s -X DELETE "$BASE/function/delete?functionApi=$api&version=1.0.0" -H "Authorization: $TOKEN"
  echo
done
```

---

## 10. 验收清单

- [ ] 4 个 runner `/health` 全 UP
- [ ] Groovy：创建 / 详情 / 编辑 / 测试 / 发布 / 执行 全绿
- [ ] Java：同上，且 JDK 镜像可用（javac 存在）
- [ ] Python：同上，且 `parameters: []` 走默认值不报 TypeError
- [ ] TypeScript：同上，且 `require` 非内置模块被拒绝（沙箱生效）
- [ ] N1/N2：坏代码创建即被拒，库里无脏数据
- [ ] `/test` 的 `data` 是对象、`/execute` 的 `data` 是字符串
- [ ] 带自定义键（`status`）的返回值不再触发 500
