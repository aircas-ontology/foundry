# python-runner：Python 语言脚本 Runner。契约与 jvm/ts runner 一致。
# 镜像只装依赖（requirements 在构建期 pip install）；业务代码 app.py / executor.py
# 不打入镜像，运行时由 docker-compose 挂载到 /app —— 改脚本只需替换宿主文件 + restart。
FROM python:3.12-slim AS runtime
WORKDIR /app

COPY requirements.txt /
RUN pip install --no-cache-dir -r /requirements.txt

# 低权限非 root 运行（沙箱基线之一）
RUN useradd --system --create-home runner
USER runner

EXPOSE 39101
ENV RUNNER_TIMEOUT_MS=5000 \
    SERVER_PORT=39101
# 代码目录 /app 为运行时挂载点；spawn 子进程执行用户脚本；单 worker，靠子进程硬超时兜底
ENTRYPOINT ["sh", "-c", "python -m uvicorn app:app --app-dir /app --host 0.0.0.0 --port ${SERVER_PORT:-39101} --workers 1"]
