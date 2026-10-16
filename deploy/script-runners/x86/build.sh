#!/usr/bin/env bash
# ============================================================
# 【有网环境】构建 x86_64 离线镜像 → 导出到本目录 images/
#
# 用法：bash build.sh [源码根目录] [jvm-runner.jar路径]
# 产物：images/jvm-runner.tar, python-runner.tar, ts-runner.tar
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

SRC_ROOT="${1:-$(cd ..; pwd)}"
JAR="${2:-}"
OUT_DIR="./images"
mkdir -p "$OUT_DIR"

echo "=== 构建 x86_64 Runner 离线镜像 ==="

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

# 构建
echo "[1/3] python-runner..."
docker build --platform linux/amd64 -t foundry/python-runner:offline "$SRC_ROOT/python-runner"

echo "[2/3] ts-runner..."
docker build --platform linux/amd64 -t foundry/ts-runner:offline "$SRC_ROOT/ts-runner"

echo "[3/3] jvm-runner..."
docker build --platform linux/amd64 -t foundry/jvm-runner:offline -f - . <<'EOF'
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
