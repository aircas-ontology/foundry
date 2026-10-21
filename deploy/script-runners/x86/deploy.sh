#!/usr/bin/env bash
# ============================================================
# 【内网 x86 服务器】一键部署：加载镜像 + 启动容器
# 用法：bash deploy.sh
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

echo "=== Foundry Script-Runners x86_64 离线部署 ==="

# 校验
for f in images/python-runner.tar images/ts-runner.tar; do
  [ ! -f "$f" ] && { echo "❌ 缺少 $f" >&2; exit 1; }
done
# 校验挂载代码目录（镜像只含依赖，Runner 代码由宿主目录提供）
[ ! -f python-runner/app.py ] && { echo "❌ 缺少挂载目录 python-runner/（需含 app.py、executor.py）" >&2; exit 1; }
[ ! -f ts-runner/server.js ] && { echo "❌ 缺少挂载目录 ts-runner/（需含 server.js）" >&2; exit 1; }
chmod -R a+rX python-runner ts-runner   # 容器内非 root 用户需可读

# 加载镜像
echo "[1/2] 加载镜像..."
docker load -i images/python-runner.tar
docker load -i images/ts-runner.tar

# 启动
echo "[2/2] 启动容器..."
docker compose up -d

# 验证
echo
sleep 10
docker compose ps
echo
echo "✅ 部署完成！验证健康："
for p in 39101 39102; do
  printf "  :%d -> " "$p"
  curl -s -m 3 "http://127.0.0.1:$p/health" 2>/dev/null && echo || echo "(等待中)"
done
echo
echo "💡 后续更新 Runner 脚本：替换 python-runner/ 或 ts-runner/ 里的文件，然后："
echo "   docker compose restart python-runner   # 或 ts-runner，无需重新打/加载镜像"
