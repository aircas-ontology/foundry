# ts-runner：TypeScript 语言脚本 Runner。契约与 jvm/python runner 一致。
FROM node:20-slim AS runtime
WORKDIR /app

# 先装依赖，利用层缓存
COPY package.json ./
RUN npm install --omit=dev && npm cache clean --force

COPY server.js ./

# 低权限非 root 运行（沙箱基线之一）
RUN useradd --system --create-home runner 2>/dev/null || adduser --system --home /app runner
USER runner

EXPOSE 39102
ENV SERVER_PORT=39102 \
    RUNNER_TIMEOUT_MS=5000
ENTRYPOINT ["node", "server.js"]
