#!/usr/bin/env bash
# ============================================================
# 【内网 x86 服务器】一键部署：加载镜像 + 启动容器
# 用法：bash deploy.sh
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

echo "=== Foundry Script-Runners x86_64 离线部署 ==="

# 校验
for f in images/jvm-runner.tar images/python-runner.tar images/ts-runner.tar; do
  [ ! -f "$f" ] && { echo "❌ 缺少 $f" >&2; exit 1; }
done

# 加载镜像
echo "[1/2] 加载镜像..."
docker load -i images/python-runner.tar
docker load -i images/ts-runner.tar
docker load -i images/jvm-runner.tar

# 启动
echo "[2/2] 启动容器..."
docker compose up -d

# 验证
echo
sleep 10
docker compose ps
echo
echo "✅ 部署完成！验证健康："
for p in 39101 39102 39103 39104; do
  printf "  :%d -> " "$p"
  curl -s -m 3 "http://127.0.0.1:$p/health" 2>/dev/null && echo || echo "(等待中)"
done
