#!/usr/bin/env bash
# ============================================================
# 【有网环境】构建 ARM64 离线镜像 → 导出到本目录 images/
# 在 x86 机器上通过 QEMU 交叉构建，也可在 ARM 机器原生构建
#
# 用法：bash build.sh [源码根目录] [jvm-runner.jar路径]
# 产物：images/jvm-runner.tar, python-runner.tar, ts-runner.tar
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

PLATFORM="linux/arm64"
SRC_ROOT="${1:-$(cd ..; pwd)}"
JAR="${2:-}"
OUT_DIR="./images"
mkdir -p "$OUT_DIR"

echo "=== 构建 ARM64 Runner 离线镜像 ==="

# 注册 QEMU 模拟器（允许 x86 主机交叉构建 arm64）
if ! docker run --privileged --rm tonistiigi/binfmt --install arm64 2>/dev/null; then
  echo "⚠️  tonistiigi/binfmt 拉取失败，尝试继续（若主机为 ARM 则无需 QEMU）"
fi

# 定位 jar
if [ -z "$JAR" ]; then
  for c in ./jvm-runner.jar "$SRC_ROOT/jvm-runner/target/jvm-runner.jar" "$SRC_ROOT/jvm-runner/jvm-runner.jar"; do
    [ -f "$c" ] && JAR="$c" && break
  done
fi
[ ! -f "$JAR" ] && { echo "❌ 找不到 jvm-runner.jar" >&2; exit 1; }
echo "==> jar: $JAR"
JAR_ABS=$(realpath "$JAR")
[ "$JAR_ABS" != "$(realpath ./jvm-runner.jar 2>/dev/null || echo '')" ] && cp -f "$JAR" ./jvm-runner.jar

# 校验源码
for d in python-runner ts-runner; do
  [ ! -f "$SRC_ROOT/$d/Dockerfile" ] && { echo "❌ $SRC_ROOT/$d/Dockerfile 不存在" >&2; exit 1; }
done
echo "==> 源码: $SRC_ROOT"
echo

# 确保 BuildKit 启用（支持 --platform）
export DOCKER_BUILDKIT=1

# 构建（用 docker build 而非 buildx，避免拉取 dockerfile frontend 镜像）
echo "[1/3] python-runner (arm64)..."
docker build --platform "$PLATFORM" -t foundry/python-runner:offline "$SRC_ROOT/python-runner"

echo "[2/3] ts-runner (arm64)..."
docker build --platform "$PLATFORM" -t foundry/ts-runner:offline "$SRC_ROOT/ts-runner"

echo "[3/3] jvm-runner (arm64)..."
docker build --platform "$PLATFORM" -t foundry/jvm-runner:offline -f - . <<'EOF'
FROM eclipse-temurin:17-jdk
WORKDIR /app
RUN useradd --system --create-home runner
COPY jvm-runner.jar /app/jvm-runner.jar
USER runner
ENV RUNNER_LANGUAGE=groovy \
    SERVER_PORT=39103
ENTRYPOINT ["sh","-c","java -jar /app/jvm-runner.jar --runner.language=${RUNNER_LANGUAGE} --server.port=${SERVER_PORT}"]
EOF

# 导出
echo
echo "[导出] -> $OUT_DIR/"
docker save foundry/python-runner:offline -o "$OUT_DIR/python-runner.tar"
docker save foundry/ts-runner:offline     -o "$OUT_DIR/ts-runner.tar"
docker save foundry/jvm-runner:offline     -o "$OUT_DIR/jvm-runner.tar"

rm -f ./jvm-runner.jar
echo
echo "✅ 完成！"
ls -lh "$OUT_DIR"/*.tar
