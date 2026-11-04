ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ARG BASE_IMAGE

WORKDIR /app
ENV HOME=/tmp/home \
    XDG_CONFIG_HOME=/tmp/home \
    XDG_CACHE_HOME=/tmp/home \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    SEMGREP_SEND_METRICS=off \
    SEMGREP_ENABLE_VERSION_CHECK=0 \
    SEMGREP_LOG_FILE=/dev/null \
    SEMGREP_SETTINGS_FILE=/tmp/home/settings.yml
COPY requirements.txt ./
RUN python -c "from importlib.metadata import version; pins=[line.strip().split('==') for line in open('requirements.txt') if line.strip() and not line.startswith('#')]; assert all(version(name)==pinned for name,pinned in pins), 'Base dependency mismatch'"
COPY base-image.json version-manifest.json Dockerfile ./
COPY app/ ./app/
COPY tools/prepare.py ./tools/prepare.py
COPY rules/ ./rules/
RUN python -c "import json,os; assert json.load(open('base-image.json'))['reference']==os.environ['BASE_IMAGE']" && python tools/prepare.py --check && chmod -R a-w /app
USER root
EXPOSE 8080
ENTRYPOINT ["python", "-m", "uvicorn", "app.server:application", "--host", "0.0.0.0", "--port", "8080", "--workers", "1", "--no-access-log", "--limit-concurrency", "64", "--timeout-graceful-shutdown", "25"]
