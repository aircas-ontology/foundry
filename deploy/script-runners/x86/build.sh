#!/usr/bin/env bash
# ============================================================
# 【有网环境】构建 x86_64 离线镜像 → 导出到本目录 images/
#
# 用法：bash build.sh [源码根目录]（默认为 foundry/script-runners）
# 产物：images/python-runner.tar, ts-runner.tar（仅含依赖）
#       python-runner/, ts-runner/（挂载用业务代码目录，与源码同名，随部署包一起拷贝到服务器）
# 说明：仅构建 Python/TS Runner；Groovy/Java 在主程序进程内执行，无需容器
#       镜像内不再打入业务代码，服务器改脚本只需替换 python-runner/ ts-runner/ 里的文件后 restart
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

SRC_ROOT="${1:-$(cd ../../..; pwd)/script-runners}"
OUT_DIR="./images"
mkdir -p "$OUT_DIR"

echo "=== 构建 x86_64 Runner 离线镜像 ==="

# 校验源码
for d in python-runner ts-runner; do
  [ ! -f "$SRC_ROOT/$d/Dockerfile" ] && { echo "❌ $SRC_ROOT/$d/Dockerfile 不存在" >&2; exit 1; }
done
echo "==> 源码: $SRC_ROOT"
echo

# 构建
echo "[1/2] python-runner..."
docker build --platform linux/amd64 -t foundry/python-runner:offline "$SRC_ROOT/python-runner"

echo "[2/2] ts-runner..."
docker build --platform linux/amd64 -t foundry/ts-runner:offline "$SRC_ROOT/ts-runner"

# 导出
echo
echo "[导出] -> $OUT_DIR/"
docker save foundry/python-runner:offline -o "$OUT_DIR/python-runner.tar"
docker save foundry/ts-runner:offline     -o "$OUT_DIR/ts-runner.tar"

# 准备挂载代码目录（与 compose 同路径拷贝到服务器即可；改脚本无需重新打镜像）
echo
echo "[挂载目录] -> ./python-runner ./ts-runner"
mkdir -p python-runner ts-runner
cp "$SRC_ROOT/python-runner/app.py" "$SRC_ROOT/python-runner/executor.py" "$SRC_ROOT/python-runner/requirements.txt" python-runner/
cp "$SRC_ROOT/ts-runner/server.js" "$SRC_ROOT/ts-runner/package.json" ts-runner/
chmod -R a+rX python-runner ts-runner

echo
echo "✅ 完成！"
ls -lh "$OUT_DIR"/*.tar
