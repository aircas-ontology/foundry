# ts-runner：TypeScript 语言脚本 Runner。契约与 jvm/python runner 一致。
# 镜像只装依赖（typescript 装在 /node_modules，作为 /app 的上级目录供 Node 逐级查找）；
# 业务代码 server.js 不打入镜像，运行时由 docker-compose 挂载到 /app —— 改脚本只需替换宿主文件 + restart。
FROM node:20-slim AS runtime

# 依赖装在根目录：容器内 /app/node_modules 会被挂载覆盖，/node_modules 不受影响
COPY package.json /
RUN npm install --omit=dev --prefix / && npm cache clean --force

# 基础镜像自带非 root 用户 node（UID 1000），直接复用
USER node

EXPOSE 39102
ENV SERVER_PORT=39102 \
    RUNNER_TIMEOUT_MS=5000
# /app 为运行时挂载点，入口脚本由宿主提供
ENTRYPOINT ["sh", "-c", "node /app/server.js"]
