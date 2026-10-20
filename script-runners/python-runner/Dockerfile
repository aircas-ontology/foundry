# python-runner：Python 语言脚本 Runner。契约与 jvm/ts runner 一致。
FROM python:3.12-slim AS runtime
WORKDIR /app

# 先装依赖，利用层缓存
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py executor.py ./

# 低权限非 root 运行（沙箱基线之一）
RUN useradd --system --create-home runner
USER runner

EXPOSE 39101
ENV RUNNER_TIMEOUT_MS=5000 \
    SERVER_PORT=39101
# spawn 子进程执行用户脚本；单 worker，靠子进程硬超时兜底
ENTRYPOINT ["sh", "-c", "python -m uvicorn app:app --host 0.0.0.0 --port ${SERVER_PORT:-39101} --workers 1"]
