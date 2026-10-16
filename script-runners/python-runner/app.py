"""python-runner：Python 语言脚本 Runner（FastAPI）。

对外契约与 groovy/java/ts runner 完全一致：
  GET  /health           → {status, language}
  POST /validate  {code} → {ok} | {ok:false, error}
  POST /functions/handle {functionApi,version,code,input}
                         → {data,description,taskId,..., _meta:{durationMs,status,language}}

主应用（ontology-server）不执行任何用户代码，统一 HTTP 调本服务。
"""

import os
import time

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel

import executor
from executor import ScriptError

app = FastAPI(title="python-runner", docs_url=None, redoc_url=None)

TIMEOUT_MS = int(os.getenv("RUNNER_TIMEOUT_MS", "5000"))


class ValidateRequest(BaseModel):
    code: str


class HandleRequest(BaseModel):
    functionApi: str | None = None
    version: str | None = None
    code: str
    input: dict | None = None


def _normalize(result: object) -> dict:
    """归一化为 FunctionResultVO 形态：dict 原样展开；其他裸值包进 data。"""
    if isinstance(result, dict):
        return dict(result)
    return {"data": result}


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "language": "python"}


@app.post("/validate")
def validate(req: ValidateRequest) -> dict:
    try:
        executor.validate(req.code)
        return {"ok": True}
    except ScriptError as e:
        return {"ok": False, "error": str(e)}
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "error": f"{type(e).__name__}: {e}"}


@app.post("/parse")
def parse(req: ValidateRequest) -> dict:
    """静态解析脚本输入/输出参数（ast），不执行用户代码。"""
    try:
        params = executor.parse(req.code)
        return {"ok": True, "params": params}
    except SyntaxError as e:
        return {"ok": False, "error": f"Python 语法错误：{e.msg}（line {e.lineno}）"}
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "error": f"{type(e).__name__}: {e}"}


@app.post("/functions/handle")
def handle(req: HandleRequest):
    start = time.time()
    try:
        result = executor.execute(req.code, req.input or {}, TIMEOUT_MS)
    except ScriptError as e:
        return JSONResponse(status_code=400, content={"ok": False, "error": str(e)})
    resp = _normalize(result)
    resp["_meta"] = {
        "durationMs": int((time.time() - start) * 1000),
        "status": "ok",
        "language": "python",
    }
    return resp
