# Semgrep 扫描服务

基于 Semgrep CE 的 HTTP 静态安全检测服务，支持 Groovy、Python 和 TypeScript。规则随镜像提供，扫描时不需要访问外网，不执行用户脚本。

## 环境要求

- Docker Engine / Docker Desktop（Linux 容器）及 Docker Compose V2 或兼容版本。
- 平台：`linux/amd64`。
- Linux 构建需要 Bash、Python 3；Windows 使用 PowerShell。
- 基础镜像版本和 digest 固定在 `base-image.json`，首次构建需要拉取镜像。

## 构建与启动

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

默认地址：`http://127.0.0.1:18080`。启动后需要等待引擎和规则自检完成，通过 capabilities 接口确认就绪。

## 配置

按需复制 `.env.example` 为 `.env`：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| SCAN_BIND_ADDRESS | 127.0.0.1 | 宿主监听地址 |
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

## 常用命令

在新终端使用 Compose 前，先加载基础镜像引用；自定义标签还需设置同名的 `SCAN_IMAGE`。

**PowerShell：**

```powershell
$env:SEMGREP_BASE_IMAGE = (Get-Content ./base-image.json -Raw | ConvertFrom-Json).reference
# 自定义标签时：$env:SCAN_IMAGE = 'foundry/semgrep-scan:1.0'
```

**Linux：**

```bash
export SEMGREP_BASE_IMAGE="$(python3 -c 'import json; print(json.load(open("base-image.json"))["reference"])')"
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

## 接口

| 接口 | 说明 |
| --- | --- |
| GET /api/v1/health | 存活检查 |
| GET /api/v1/capabilities | 就绪状态、支持语言、引擎版本、规则版本和扫描限额 |
| POST /api/v1/scan | 同步返回扫描结果 |

检测状态：

- `PASSED`：扫描完整且未命中阻断规则，可以包含提示性 findings。
- `REJECTED`：扫描完整且命中阻断规则。
- `ERROR`：解析失败、扫描不完整、超时或其他异常。

扫描结论使用 HTTP 200；请求错误为 400，满载为 429，未就绪为 503，服务故障为 500。health 正常不代表扫描已就绪。

请求字段：`requestId`（追踪标识）、`scriptType`（`GROOVY` / `PYTHON` / `TYPESCRIPT`）、`code`、`codeHash`。hash 为原始代码 UTF-8 字节的 SHA-256 小写十六进制，不转换换行或空白。不接受自定义命令参数或规则路径。

PowerShell 调用示例：

```powershell
Invoke-RestMethod http://127.0.0.1:18080/api/v1/capabilities
$code = 'print("hello")'
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $hash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($code)))).Replace('-', '').ToLowerInvariant()
} finally { $sha.Dispose() }
$body = @{requestId='example-1'; scriptType='PYTHON'; code=$code; codeHash=$hash} | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:18080/api/v1/scan -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

Linux 调用示例：

```bash
python3 - <<'PY'
import hashlib, json, urllib.request
code = 'print("hello")'
body = dict(requestId='example-1', scriptType='PYTHON', code=code,
            codeHash=hashlib.sha256(code.encode('utf-8')).hexdigest())
req = urllib.request.Request('http://127.0.0.1:18080/api/v1/scan',
    data=json.dumps(body).encode(), headers={'Content-Type': 'application/json'})
print(urllib.request.urlopen(req).read().decode())
PY
```

响应包含 `scanId`、`verdict`、`durationMs`、`cached`、`findings` 和 `findingsTruncated`；异常附 `error.code/message/retryable`。缓存关闭，`cached=false`。位置从 1 起算，列按 Unicode 字符计，end 为排他边界。

## 规则与运行限制

- Groovy 使用 Java 解析器；不兼容的 Groovy 语法返回 ERROR，不保证覆盖全部动态语义。
- `rules/active/` 是实际运行规则，`rules/blocking.json` 按明确规则 ID 决定阻断，其余命中仅提示；保留规则自带的来源和许可证信息。
- `parse-integrity` 规则用于确保无危险关键词的代码也被解析，应与安全规则一起保留。
- 代码上限 256 KiB，原始报告上限 4 MiB，展示最多 200 条 findings；展示截断不影响全量阻断判定。
- 单 worker、共享两扫描槽位、每次 jobs=1；引擎超时 15s，总预算 20s。
- 容器默认 2 CPU、2 GiB 内存、512 PID、512 MiB tmpfs；非 root、只读根文件系统，不挂载 Docker socket。
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
