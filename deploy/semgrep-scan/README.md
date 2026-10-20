# Semgrep 扫描服务

基于 Semgrep CE 的 HTTP 静态安全检测服务，支持 Groovy、Python 和 TypeScript。规则随镜像提供，扫描时不需要访问外网，不执行用户脚本。

## 文档导航

- [部署方式选择](#部署方式选择)
- [外网环境：构建并启动](#外网环境构建并启动)
- [配置说明](#配置说明)
- [日常运维](#日常运维)
- [内网环境：离线部署](#内网环境离线部署)
- [接口](#接口)
- [规则与运行限制](#规则与运行限制)
- [更新规则与排障](#更新规则与排障)

## 部署方式选择

| 场景 | 镜像来源 | Compose 命令 | Compose 文件 |
| --- | --- | --- | --- |
| 可访问外网，Compose V2 | 在目标机拉取基础镜像并构建 | `docker compose` | `docker-compose.yml` |
| 内网离线，Compose V2 | 导入已构建的应用镜像 tar 包 | `docker compose` | `docker-compose.yml` |
| 内网离线，Compose v1.27 | 导入已构建的应用镜像 tar 包 | `docker-compose` | `docker-compose.v1.yml` |

镜像支持 `linux/amd64` 和 `linux/arm64`，但离线 tar 包必须包含目标服务器对应的架构。Compose V1 专用文件移除了 v1.27 不支持的顶层 `name` 和 `pull_policy`；启动命令也不使用 V2 的 `--pull never` 参数。

## 环境要求

- Docker Engine / Docker Desktop（Linux 容器）。构建流程使用 Docker Compose V2；内网离线部署另支持 Docker Compose v1.27。
- 平台：x86-64（`linux/amd64`）和 ARM64（`linux/arm64`）。固定 digest 是同时包含两种架构的 OCI index。
- Linux 构建需要 Bash、Python 3；Windows 使用 PowerShell。
- 基础镜像版本和 digest 固定在 `base-image.json`，首次构建需要拉取镜像。

## 外网环境：构建并启动

在本目录执行：

**Windows PowerShell：**

```powershell
./build.ps1 -Start
```

**Linux：**

```bash
bash ./build.sh --start
```

脚本拉取固定基础镜像，构建本地镜像 `foundry/semgrep-scan:local` 并启动容器；任何步骤失败立即停止。只构建时省略 `-Start` / `--start`。

默认按 Docker 服务端架构构建。交叉构建需 Docker Desktop/QEMU 支持目标架构：

```powershell
./build.ps1 -Platform linux/arm64 -Image foundry/semgrep-scan:arm64
```

```bash
SCAN_PLATFORM=linux/arm64 SCAN_IMAGE=foundry/semgrep-scan:arm64 bash ./build.sh
```

发布双架构镜像（将标签替换为可写仓库地址）：

```bash
docker buildx build --platform linux/amd64,linux/arm64 --build-arg BASE_IMAGE="$SEMGREP_BASE_IMAGE" -t registry.example.com/foundry/semgrep-scan:1.0 --push .
```

默认监听所有宿主机网络接口（`0.0.0.0:18080`）。本机可通过 `http://localhost:18080` 访问，其他内网机器使用 `http://<服务器内网 IP>:18080`。启动后需要等待引擎和规则自检完成，通过 capabilities 接口确认就绪。

## 配置说明

按需复制 `.env.example` 为 `.env`：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| SCAN_BIND_ADDRESS | 0.0.0.0 | 宿主监听地址；允许其他内网机器通过服务器内网 IP 访问 |
| SCAN_PORT | 18080 | 宿主端口 |
| SCAN_NETWORK_INTERNAL | false | 启用 Docker 内部网络，限制外网访问 |

内部网络模式通过同网络的 `semgrep-scan:8080` 访问；此模式可能使宿主发布端口不可用。默认 bridge 网络不限制出口。对其他主机开放服务时，应通过内网网关配置鉴权、TLS 和访问控制，限制为业务后端访问。

自定义镜像标签：

```powershell
./build.ps1 -Image foundry/semgrep-scan:1.0 -Start
```

```bash
SCAN_IMAGE=foundry/semgrep-scan:1.0 bash ./build.sh --start
```

## 日常运维

在新终端使用 Compose 前，先加载基础镜像引用；自定义标签还需设置同名的 `SCAN_IMAGE`。

**PowerShell：**

```powershell
$env:SEMGREP_BASE_IMAGE = (Get-Content ./base-image.json -Raw | ConvertFrom-Json).reference
$env:SCAN_PLATFORM = 'linux/' + (docker version --format '{{.Server.Arch}}')
# 自定义标签时：$env:SCAN_IMAGE = 'foundry/semgrep-scan:1.0'
```

**Linux：**

```bash
export SEMGREP_BASE_IMAGE="$(python3 -c 'import json; print(json.load(open("base-image.json"))["reference"])')"
export SCAN_PLATFORM="linux/$(docker version --format '{{.Server.Arch}}')"
# 自定义标签时：export SCAN_IMAGE=foundry/semgrep-scan:1.0
```

```bash
docker compose up -d --no-build --pull never
docker compose ps
docker compose logs --tail 50
docker compose restart semgrep-scan
docker compose down
```

启动现有镜像时使用 `--no-build --pull never`，不会隐式拉取或重建服务镜像。源文件或规则更新后重新运行构建脚本。

## 内网环境：离线部署

本节适用于无法访问公网或镜像仓库的 Linux 服务器。应用代码、依赖和规则已包含在应用镜像中，内网服务器不需要重新构建。

### 1. 准备与目标架构一致的部署包

先在内网服务器确认 Docker 架构和 Compose 版本：

```bash
docker version --format '{{.Server.Arch}}'
docker compose version 2>/dev/null || docker-compose version
```

架构输出应为 `amd64` 或 `arm64`。根据结果选择对应镜像包，不能在 amd64 服务器上导入仅含 arm64 的镜像，反之亦然。

| 服务器架构 | 镜像平台 | 建议 tar 文件名 |
| --- | --- | --- |
| x86-64 | `linux/amd64` | `semgrep-scan-linux-amd64.tar` |
| ARM64 / AArch64 | `linux/arm64` | `semgrep-scan-linux-arm64.tar` |

将以下文件复制到内网服务器的独立目录，例如 `/opt/semgrep-scan`：

```text
/opt/semgrep-scan/
├── semgrep-scan-linux-amd64.tar  # 或 semgrep-scan-linux-arm64.tar
├── docker-compose.yml            # Compose V2 使用
├── docker-compose.v1.yml         # Compose v1.27 使用
└── base-image.json
```

不需要复制 `Dockerfile`、`app/`、`rules/`、`requirements.txt` 或构建脚本。

> 打包方应通过 `docker image inspect <镜像> --format '{{.Architecture}}'` 核对架构，再用 `docker save -o <tar文件> <镜像>` 导出。不要只根据 tar 文件名判断架构。

### 2. 导入并核对镜像

```bash
cd /opt/semgrep-scan
docker load -i semgrep-scan-linux-amd64.tar  # ARM64 时改为对应文件
docker images --filter reference='*semgrep-scan*'
docker image inspect foundry/semgrep-scan:local --format '{{.Architecture}}'
```

后续示例假定导入的镜像名为 `foundry/semgrep-scan:local`。如果 `docker load` 显示的名称或标签不同，按实际值设置 `SCAN_IMAGE`。

### 3. 创建 `.env`

在部署目录创建 `.env`：

```bash
cd /opt/semgrep-scan
cat > .env <<EOF
SCAN_IMAGE=foundry/semgrep-scan:local
SCAN_PLATFORM=linux/$(docker version --format '{{.Server.Arch}}')
SEMGREP_BASE_IMAGE=$(python3 -c 'import json; print(json.load(open("base-image.json"))["reference"])')
SCAN_BIND_ADDRESS=0.0.0.0
SCAN_PORT=18080
SCAN_NETWORK_INTERNAL=false
EOF
```

`SEMGREP_BASE_IMAGE` 只用于 Compose 解析 `build` 配置；使用下文的禁止构建参数启动时，不会拉取或运行该基础镜像。若服务器没有 Python 3，可读取 `base-image.json`，将 `reference` 的完整值手工填入 `.env`。

默认监听所有宿主机网络接口，其他内网机器可通过服务器内网 IP 访问。请通过防火墙或内网网关仅允许可信业务服务器和调试机器访问 `SCAN_PORT`；服务本身不提供鉴权，不应将该端口直接暴露到公网。

### 4. 启动服务

Compose V2：

```bash
docker compose config --quiet
docker compose up -d --no-build --pull never
```

Docker Compose v1.27：

```bash
docker-compose -f docker-compose.v1.yml config --quiet
docker-compose -f docker-compose.v1.yml up -d --no-build
```

V1 启动前必须确认 `SCAN_IMAGE` 已导入且名称完全匹配。`--no-build` 禁止在内网服务器构建；v1.27 没有 `up --pull never`，因此专用 Compose 文件不声明镜像拉取策略。若镜像不存在，Compose V1 仍可能尝试从仓库拉取并失败，此时应修正 `SCAN_IMAGE` 或重新导入镜像。

### 5. 验证服务

```bash
curl -fsS http://localhost:18080/api/v1/health
curl -fsS http://localhost:18080/api/v1/capabilities
```

检查容器健康状态：

```bash
# Compose V2
docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q semgrep-scan)"

# Compose v1.27
docker inspect --format '{{.State.Health.Status}}' "$(docker-compose -f docker-compose.v1.yml ps -q semgrep-scan)"
```

`health` 成功只表示 HTTP 进程存活。只有 `capabilities` 中的 `ready` 为 `true` 且容器状态为 `healthy`，才表示引擎和规则已就绪。

### 6. 内网环境日常操作与更新

Compose V2：

```bash
docker compose ps
docker compose logs -f semgrep-scan
docker compose restart semgrep-scan
docker compose down
```

Compose v1.27：

```bash
docker-compose -f docker-compose.v1.yml ps
docker-compose -f docker-compose.v1.yml logs -f semgrep-scan
docker-compose -f docker-compose.v1.yml restart semgrep-scan
docker-compose -f docker-compose.v1.yml down
```

更新时先导入新 tar 包；若镜像标签改变，同时更新 `.env` 中的 `SCAN_IMAGE`。然后强制重建容器：

```bash
# Compose V2
docker compose up -d --no-build --pull never --force-recreate

# Compose v1.27
docker-compose -f docker-compose.v1.yml up -d --no-build --force-recreate
```

更新完成后重新检查 health、capabilities 和容器健康状态。

### 7. 内网部署常见问题

- 提示 `SCAN_PLATFORM` 或 `SEMGREP_BASE_IMAGE` 未设置：确认 `.env` 和所用 Compose 文件位于同一目录，并从该目录执行命令。
- Compose 尝试拉取镜像：确认 `docker images` 中的镜像名称、标签与 `.env` 的 `SCAN_IMAGE` 完全一致。V2 还应保留 `--pull never`。
- 出现 `exec format error`：镜像架构与服务器不一致，分别用 `docker image inspect` 和 `docker version --format '{{.Server.Arch}}'` 核对。
- 其他内网主机无法访问：确认 `SCAN_BIND_ADDRESS=0.0.0.0`，并检查宿主机防火墙、网关策略和 `SCAN_PORT`。
- 容器启动后退出：使用对应 Compose 命令执行 `ps -a` 和 `logs --tail 200 semgrep-scan` 查看原因。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /api/v1/health | 存活检查 |
| GET | /api/v1/capabilities | 就绪状态、支持语言、引擎版本、规则版本和扫描限额 |
| POST | /api/v1/scan | 同步返回扫描结果 |

### 通用约定

- 请求和响应均为 `application/json; charset=utf-8`，响应带 `Cache-Control: no-store`。
- JSON 字段使用小驼峰命名。
- 下文示例中的 `//` 注释仅用于说明字段，实际请求和响应不包含注释。
- health 正常不代表扫描已就绪；能否扫描以 capabilities 的 `ready` 为准。

### GET /api/v1/health

- 请求：无参数、无请求体。
- 用途：仅探测 HTTP 进程是否存活。

请求示例：

```powershell
Invoke-RestMethod http://localhost:18080/api/v1/health
```

```bash
curl -s http://localhost:18080/api/v1/health
```

响应示例（HTTP 200）：

```jsonc
{
  "status": "alive"   // string：固定为 "alive"，表示进程在响应；不代表规则或引擎已就绪
}
```

### GET /api/v1/capabilities

- 请求：无参数、无请求体。
- 用途：获取就绪状态、支持语言、引擎/规则版本和扫描限额。
- 客户端应等待 `ready=true` 后再调用扫描接口。

请求示例：

```powershell
Invoke-RestMethod http://localhost:18080/api/v1/capabilities
```

```bash
curl -s http://localhost:18080/api/v1/capabilities
```

响应示例（HTTP 200，`ready=true`）：

```jsonc
{
  "ready": true,                            // boolean：启动自检（manifest、规则、引擎冒烟扫描）全部通过；false 时 HTTP 503
  "languages": {                            // object：支持的脚本类型 → 实际使用的解析器
    "GROOVY": "java",                       // string：Groovy 由 Java 解析器处理
    "PYTHON": "python",                     // string：Python 解析器
    "TYPESCRIPT": "typescript"              // string：TypeScript 解析器
  },
  "engine": "Semgrep CE",                   // string：扫描引擎产品名
  "engineVersion": "1.180.0",               // string：引擎版本，与 manifest 锁定值一致
  "baseImageDigest": "sha256:529ee8a2...",  // string：基础镜像 digest（示例省略中间部分）
  "platform": "linux/amd64",                // string：当前容器运行架构，取值为 linux/amd64 或 linux/arm64
  "supportedPlatforms": [                   // array<string>：镜像支持的发布平台
    "linux/amd64",
    "linux/arm64"
  ],
  "serviceBuildId": "9165fb3ac97cf2bd",     // string：服务构建 ID
  "rulesetVersion": "sha256:9165fb3a...",   // string：规则集指纹，规则/源码/依赖变化后会变化
  "limits": {                               // object：运行时扫描限额
    "slots": 2,                             // number：并发扫描槽位数，占满时返回 429
    "jobs": 1,                              // number：每次扫描的引擎并发数
    "engineTimeoutSeconds": 15,             // number：单次引擎超时（秒）
    "totalTimeoutSeconds": 20,              // number：单次请求总预算（秒）
    "codeBytes": 262144,                    // number：代码 UTF-8 字节上限（256 KiB）
    "reportBytes": 4194304,                 // number：原始报告上限（4 MiB）
    "diagnosticBytes": 1048576,             // number：诊断输出读取上限（1 MiB）
    "diagnosticLines": 200,                 // number：诊断输出行数上限
    "findings": 200                         // number：单次最多返回的 findings 数
  },
  "cached": false                           // boolean：固定为 false，缓存已关闭
}
```

未就绪时的响应（HTTP 503）：

- manifest 和规则已加载、自检未完成或失败：响应体仍是上面的 capabilities 结构，仅 `ready=false`。
- manifest 尚未加载：响应体为扫描接口的错误结构，`error.code=SERVICE_NOT_READY`。

### POST /api/v1/scan

- 同步返回扫描结果；不返回 PASSED/REJECTED 等业务结论。
- 是否放行由本体服务根据 `error`、`findings` 的等级/类别和自身策略决定。
- 响应含 `error` 或 `findingsTruncated=true` 时，本体服务必须显式处理，不能视为"无问题"。
- 必须使用 `Content-Type: application/json`；且只接受下列四个字段，不支持自定义命令参数或规则路径，重复 JSON 键会被拒绝。
- HTTP 请求体上限为 2 MiB，读取超时为 5 秒。

请求字段：

```jsonc
{
  "requestId": "function-save-123",  // string，必填：追踪标识，1–128 位，仅允许 [A-Za-z0-9_.:-]
  "scriptType": "GROOVY",            // string，必填：脚本类型，取值为 GROOVY / PYTHON / TYPESCRIPT
  "code": "class A {}",              // string，必填：待扫描源码；不能为空或全空白、不能含 NUL（U+0000）字符、UTF-8 不超过 256 KiB
  "codeHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                                     // string，必填：code 原始 UTF-8 字节的 SHA-256，64 位小写十六进制；
                                     //   必须与 code 实际 hash 一致，不转换换行或空白（示例值为占位）
}
```

#### 请求示例（PowerShell）

```powershell
Invoke-RestMethod http://localhost:18080/api/v1/capabilities
$code = 'print("hello")'
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $hash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($code)))).Replace('-', '').ToLowerInvariant()
} finally { $sha.Dispose() }
$body = @{requestId='example-1'; scriptType='PYTHON'; code=$code; codeHash=$hash} | ConvertTo-Json
Invoke-RestMethod http://localhost:18080/api/v1/scan -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

#### 请求示例（Linux）

```bash
python3 - <<'PY'
import hashlib, json, urllib.request
code = 'print("hello")'
body = dict(requestId='example-1', scriptType='PYTHON', code=code,
            codeHash=hashlib.sha256(code.encode('utf-8')).hexdigest())
req = urllib.request.Request('http://localhost:18080/api/v1/scan',
    data=json.dumps(body).encode(), headers={'Content-Type': 'application/json'})
print(urllib.request.urlopen(req).read().decode())
PY
```

#### 响应示例（有命中，HTTP 200）

扫描 `while True:\n    pass\n`，`scanId` 和 `durationMs` 为示意值：

```jsonc
{
  "scanId": "f1551d85a6a14d5b89660a80695190ac",  // string|null：扫描追踪 ID（UUID4 去连字符）；HTTP 层直接拒绝时为 null
  "durationMs": 850,                             // number：扫描耗时（毫秒，向下取整）
  "cached": false,                               // boolean：固定为 false，缓存已关闭
  "findings": [                                  // array：命中列表，按严重度、起始位置、ruleId 排序，最多 200 条
    {
      "ruleId": "foundry.python.resource-exhaustion", // string：命中的规则 ID
      "category": "resource-exhaustion",              // string：问题类别，如 resource-exhaustion、sql-injection、security
      "severity": "MEDIUM",                           // string：HIGH / MEDIUM / LOW，对应引擎 ERROR / WARNING / INFO；仅风险提示，不等于业务阻断
      "message": "检测到无条件循环。",                  // string：一句话描述，来自服务内置静态文案
      "reason": "无条件循环可能持续消耗 CPU；需审核退出条件及运行时超时。存在 break 的循环也可能命中。",
                                                      // string：原因与审核建议，来自规则说明或 CWE
      "startLine": 1,                                 // number|null：起点行，从 1 起算；位置无法换算时为 null
      "startColumn": 1,                               // number|null：起点列，从 1 起算，按 Unicode 字符计
      "endLine": 2,                                   // number|null：终点行
      "endColumn": 9                                  // number|null：终点列，排他边界（最后一个字符之后）
    }
  ],
  "findingsTruncated": false                     // boolean：true 表示命中超过 200 条，findings 不是完整列表
}
```

无命中时 `findings` 为 `[]`，且响应中不含 `error`。

#### 响应示例（扫描失败，HTTP 200）

```jsonc
{
  "scanId": "f1551d85a6a14d5b89660a80695190ac",  // string|null：同上
  "durationMs": 850,                             // number：同上
  "cached": false,                               // boolean：固定为 false
  "findings": [],                                // array：出错时恒为空，不能据此判断代码安全
  "findingsTruncated": false,                    // boolean：出错时恒为 false
  "error": {                                     // object：仅失败时出现
    "code": "PARSE_FAILED",                      // string：错误码，见下表
    "message": "PARSE_FAILED",                   // string：当前恒等于 code，保留为人读描述字段
    "retryable": false                           // boolean：true 表示可以重试
  }
}
```

错误码：

| code | 含义 | retryable |
| --- | --- | --- |
| INVALID_REQUEST | 请求体、JSON、Content-Type 或字段不合法 | false |
| UNSUPPORTED_LANGUAGE | `scriptType` 不在支持列表 | false |
| CODE_HASH_MISMATCH | `codeHash` 与 `code` 的实际 SHA-256 不一致 | false |
| PARSE_FAILED / PARTIAL_PARSE | 解析失败，或只解析了部分代码 | false |
| TARGET_SKIPPED | 引擎跳过了目标文件 | false |
| REPORT_INVALID / REPORT_MISSING / REPORT_TOO_LARGE | 引擎报告缺失、格式异常或超过 4 MiB | false |
| RULESET_INVALID | 规则、版本或 manifest 校验失败（通常导致启动阶段不就绪） | false |
| SCAN_TIMEOUT | 引擎超时或超出总预算 | true |
| ENGINE_CRASHED | 引擎异常退出 | true |
| RESOURCE_UNAVAILABLE | 内存、进程等资源不足 | true |
| SERVICE_BUSY | 扫描槽位已满（HTTP 429） | true |
| SERVICE_NOT_READY | 服务未就绪（HTTP 503） | true |
| SERVICE_FAILURE | 服务内部异常（HTTP 500） | true |

#### HTTP 状态码

| 状态码 | 场景 | 响应体 |
| --- | --- | --- |
| 200 | 引擎完成扫描，包括 `PARSE_FAILED`、`SCAN_TIMEOUT` 等扫描内错误 | 扫描响应结构 |
| 400 | 请求错误（JSON/Content-Type/字段、语言、hash、请求体超限或读取超时） | 扫描响应结构 + `error`，`scanId=null`、`durationMs=0` |
| 404 | 路径或方法不存在 | `error.code=INVALID_REQUEST` |
| 429 | 两个扫描槽位已占满 | `error.code=SERVICE_BUSY` |
| 500 | 服务内部异常 | `error.code=SERVICE_FAILURE` |
| 503 | 服务未就绪 | `error.code=SERVICE_NOT_READY` |

调用方的判断顺序：先看 HTTP 状态码，再检查 `error` 是否存在，最后结合 `findings` 和 `findingsTruncated` 做业务决策。

### 当前 Spring Boot 项目的调用示例

`ontology/ontology-server` 使用 Java 17、Spring Boot 3 和 starter-web，可直接使用 `RestClient` 与 Jackson，无需新增依赖。例如在现有服务中调用：

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.web.client.RestClient;

RestClient client = RestClient.builder()
    .baseUrl("http://localhost:18080")
    .build();

String codeHash = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));

Map<?, ?> report = client.post()
    .uri("/api/v1/scan")
    .body(Map.of(
        "requestId", "function-save-123",
        "scriptType", "GROOVY",
        "code", code,
        "codeHash", codeHash))
    .retrieve()
    .body(Map.class);

// 本体服务根据 error、findings 和 findingsTruncated 决定是否保存或编译。
```

接入注意事项：

- 本体服务在容器内运行时，将地址改为同网络的 `http://semgrep-scan:8080`。
- 当前 `GroovyServiceImpl.parseGroovyCode()` 会调用 `compile(code)`，`executeGroovy()` 也可能编译并执行数据库中的代码。
- 若接入校验，应在上述编译/执行之前以及代码保存、更新入口调用，不能先编译再扫描。
- 本示例不改变已有业务放行策略。
- HTTP 非 2xx、连接失败或读取超时应走扫描不可用分支，不能当作空 findings。

## 规则与运行限制

- Groovy 使用 Java 解析器；不兼容的 Groovy 语法会在响应中返回 `error`，不保证覆盖全部动态语义。
- 解析器可能容忍部分不完整语法；未返回 error 仅表示引擎完成扫描，不是语法正确性或安全性证明。
- `rules/active/` 是实际运行规则；共 Java 104、Python 159、TypeScript 82 条。包含已导入的 SQL 注入、反序列化等安全规则，并非全部上游规则。保留规则自带的来源和许可证信息。`rules/blocking.json` 保留原文件名以减少迁移，仅提供部分规则的类别、描述和原因，不再决定阻断。
- SQL 注入：已有 Java JDBC/JPA/Hibernate/Spring、Python Django/SQLAlchemy、TypeScript Knex/云函数 SQL 规则。框架污点源不一定适用于独立脚本，因此新增三种语言的拼接/插值 SQL 执行参数检查（`foundry.<language>.sql-injection`）。变量传播、封装调用及动态 Groovy 语义仍可能漏报。
- 资源耗尽：新增三种语言无条件 while/for 循环审计（`foundry.<language>.resource-exhaustion`）。这不是终止性证明；含 break 的循环可能误报，递归、巨大分配、输入相关复杂度及 ReDoS 不保证覆盖。运行时仍需要独立执行隔离和资源限额，扫描容器的限额只保护扫描过程。
- `parse-integrity` 规则用于确保无危险关键词的代码也被解析，应与安全规则一起保留。
- 代码上限 256 KiB，原始报告上限 4 MiB，展示最多 200 条 findings；截断时 findingsTruncated=true，本体服务不能将其视为完整问题列表。
- 单 worker、共享两扫描槽位、每次 jobs=1；引擎超时 15s，总预算 20s。
- 容器默认 root，2 CPU、2 GiB 内存、512 PID、512 MiB tmpfs；只读根文件系统，不挂载 Docker socket。
- 日志不记录代码正文；本地轮转为 10 MiB × 3。

## 更新规则与排障

`version-manifest.json` 校验服务源码、依赖、基础镜像和规则指纹。修改规则或源码后，先使用固定基础镜像更新 manifest，再重新构建。

```powershell
$base = (Get-Content ./base-image.json -Raw | ConvertFrom-Json).reference
docker run --rm --network none -e PYTHONDONTWRITEBYTECODE=1 --mount "type=bind,source=$PWD,target=/work" --entrypoint python $base /work/tools/prepare.py
./build.ps1 -Start
```

```bash
base="$(python3 -c 'import json; print(json.load(open("base-image.json"))["reference"])')"
docker run --rm --network none -e PYTHONDONTWRITEBYTECODE=1 --mount "type=bind,source=$(pwd),target=/work" --entrypoint python "$base" /work/tools/prepare.py
bash ./build.sh --start
```

503 持续不恢复时，检查日志以及 manifest、规则和依赖是否一致；429 表示扫描槽位已满，应稍后重试。不要通过关闭完整性校验或增加独立 HTTP worker 绕过限制。
