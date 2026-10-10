# 脚本安全规则包

- 规则集版本：`2026.10.1`
- 适配镜像：`semgrep/semgrep:1.99.0`
- Python、TypeScript 使用 Semgrep 原生语法规则。
- Groovy 使用 `generic` 文本规则，仅提供有限覆盖，不能替代运行时沙箱。

生产环境只能使用本目录的离线副本，不得使用 `--config auto`、`p/...` 或远程 URL。规则发布前应在有网构建环境固定镜像、执行正反例测试并记录镜像 digest；运行时不下载规则。

本地集成验证由显式 Maven Profile 或运维验证流程启动，默认单元测试不要求 Docker。扫描容器必须无网络、只读挂载规则和源码，并以非 root 用户运行。

规则正反例位于 `tests/`。先离线预载固定镜像，再从仓库根目录运行：

```text
mvn -pl ontology/ontology-server -am -Pscript-security-rules-it verify
```

该 Profile 使用 `--pull never`、无网络、非 root 和只读挂载执行 `semgrep --test`。测试样例只允许静态扫描，禁止作为脚本运行。
