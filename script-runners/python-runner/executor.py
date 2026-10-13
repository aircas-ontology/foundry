"""Python 脚本执行器。

契约（与 groovy/java/ts runner 完全一致）：
- validate(code)：仅语法/编译校验（compile + 检查顶层 def handle），不执行。
- execute(code, input)：在独立子进程中 exec 用户代码，调用 handle(input)，硬超时后 terminate。

安全边界说明：进程内不做 builtins 白名单（会破坏 import math 等正常用法）；
真正的隔离由容器承担（非 root、只读根文件系统、默认禁出站网络、CPU/内存/超时上限）。
子进程 + 硬超时用于防止用户脚本死循环拖垮 runner 主进程。
"""

import multiprocessing as mp


class ScriptError(Exception):
    """脚本编译/执行失败。"""


def _find_handle_error(code: str) -> str | None:
    import ast

    tree = ast.parse(code)
    for node in tree.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == "handle":
            return None
    return "函数 handle 方法不存在"


def validate(code: str) -> None:
    """语法/编译校验：失败抛 ScriptError。"""
    try:
        compile(code, "<user-script>", "exec")
    except SyntaxError as e:
        raise ScriptError(f"Python 语法错误：{e.msg}（line {e.lineno}）") from e
    err = _find_handle_error(code)
    if err:
        raise ScriptError(err)


def _worker(code: str, input_data: dict, q) -> None:
    try:
        namespace: dict = {"__name__": "user_script"}
        exec(compile(code, "<user-script>", "exec"), namespace)
        handle = namespace.get("handle")
        if not callable(handle):
            q.put(("err", "函数 handle 方法不存在"))
            return
        result = handle(input_data)
        q.put(("ok", result))
    except Exception as e:  # noqa: BLE001 - 用户脚本任意异常都要回传
        q.put(("err", f"{type(e).__name__}: {e}"))


def execute(code: str, input_data: dict, timeout_ms: int = 5000) -> object:
    """在子进程中执行用户脚本，返回 handle 的结果对象。"""
    validate(code)
    ctx = mp.get_context("spawn")
    q = ctx.Queue()
    p = ctx.Process(target=_worker, args=(code, input_data if input_data is not None else {}, q))
    p.start()
    p.join(timeout_ms / 1000.0)
    if p.is_alive():
        p.terminate()
        p.join()
        raise ScriptError(f"脚本执行超时（>{timeout_ms}ms）")
    if q.empty():
        raise ScriptError(f"脚本执行失败：进程异常退出（exitcode={p.exitcode}）")
    status, payload = q.get()
    if status == "err":
        raise ScriptError(payload)
    return payload
