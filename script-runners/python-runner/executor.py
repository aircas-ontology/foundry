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


def parse(code: str) -> list:
    """静态解析脚本参数。

    与主应用约定的回传形态：[{name, type, category, source}, ...]；
    type 为语言原生类型文本（str/int/list/dict/interface 名），主应用侧统一归一。

    解析层级：
      1) ANNOTATION：handle 形参/返回值为 TypedDict/dataclass/类时展开字段；
      2) INFERRED：无类型时扫描函数体 input.attr / input["k"] / return dict 字面量。
    """
    import ast

    tree = ast.parse(code)
    handle_fn = None
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == "handle":
            handle_fn = node
            break
    if handle_fn is None:
        return []

    classes = _collect_classes(tree, ast)
    params: list = []

    args = list(handle_fn.args.args)
    annotated_single = len(args) == 1 and args[0].annotation is not None
    if annotated_single:
        ann = ast.unparse(args[0].annotation)
        if ann in classes and classes[ann]:
            for fname, ftype in classes[ann]:
                params.append({"name": fname, "type": ftype, "category": "INPUT", "source": "ANNOTATION"})
        elif ann in classes:
            # 注解指向脚本内类但无可识别字段（仅方法/动态属性）→ 退化启发扫 body
            for attr in _collect_input_attrs(handle_fn, args[0].arg, ast):
                params.append({"name": attr, "type": "object", "category": "INPUT", "source": "INFERRED"})
        else:
            params.append({"name": args[0].arg, "type": ann, "category": "INPUT", "source": "ANNOTATION"})
    elif args:
        # 多参或无注解：多参逐个落 ANNOTATION（有类型）或退化到启发层（无类型）
        for a in args:
            if a.annotation is not None:
                params.append({"name": a.arg, "type": ast.unparse(a.annotation), "category": "INPUT", "source": "ANNOTATION"})
        if all(a.annotation is None for a in args):
            for a in args:
                for attr in _collect_input_attrs(handle_fn, a.arg, ast):
                    params.append({"name": attr, "type": "object", "category": "INPUT", "source": "INFERRED"})

    rt = handle_fn.returns
    if rt is not None:
        rt_text = ast.unparse(rt)
        if rt_text in classes and classes[rt_text]:
            for fname, ftype in classes[rt_text]:
                params.append({"name": fname, "type": ftype, "category": "OUTPUT", "source": "ANNOTATION"})
        elif rt_text in classes:
            # 返回类型是脚本内类但无字段 → 退化启发 return 字面量
            for key in _collect_return_keys(handle_fn, ast):
                params.append({"name": key, "type": "object", "category": "OUTPUT", "source": "INFERRED"})
        elif rt_text != "None":
            params.append({"name": "result", "type": rt_text, "category": "OUTPUT", "source": "ANNOTATION"})
    else:
        for key in _collect_return_keys(handle_fn, ast):
            params.append({"name": key, "type": "object", "category": "OUTPUT", "source": "INFERRED"})

    return params


def _collect_classes(tree, ast_mod) -> dict:
    """收集脚本内定义的类及其字段 (name, type_text)，用于 handle 形参/返回值的展开。

    覆盖三种常见写法：
      1) 类级注解字段（dataclass / TypedDict）：`name: str`；
      2) 方法内 self 注解字段：`self.name: str = ...`；
      3) __init__ 里普通赋值（类型未知，记 object）：`self.name = ...`。
    下划线开头的私有字段不纳入（非对外参数）。
    """
    out: dict = {}
    for cls in ast_mod.walk(tree):
        if not isinstance(cls, ast_mod.ClassDef):
            continue
        fields: list = []
        seen: set = set()

        def add(name, type_text):
            if name and not name.startswith("_") and name not in seen:
                seen.add(name)
                fields.append((name, type_text))

        for stmt in cls.body:
            # 类级注解字段（dataclass / TypedDict）
            if isinstance(stmt, ast_mod.AnnAssign) and isinstance(stmt.target, ast_mod.Name):
                add(stmt.target.id, ast_mod.unparse(stmt.annotation))
            # 方法（含 __init__）里的 self.x 实例字段
            elif isinstance(stmt, (ast_mod.FunctionDef, ast_mod.AsyncFunctionDef)):
                for node in ast_mod.walk(stmt):
                    if isinstance(node, ast_mod.AnnAssign) and isinstance(node.target, ast_mod.Attribute) \
                            and isinstance(node.target.value, ast_mod.Name) and node.target.value.id == "self":
                        add(node.target.attr, ast_mod.unparse(node.annotation))
                    elif isinstance(node, ast_mod.Assign):
                        for tgt in node.targets:
                            if isinstance(tgt, ast_mod.Attribute) and isinstance(tgt.value, ast_mod.Name) \
                                    and tgt.value.id == "self":
                                add(tgt.attr, "object")
        out[cls.name] = fields
    return out


def _collect_input_attrs(fn, var_name: str, ast_mod) -> list:
    """启发层：扫描 handle 体内 input.attr / input["k"] / input.get("k") 使用。"""
    seen: list = []
    for node in ast_mod.walk(fn):
        if isinstance(node, ast_mod.Attribute) and isinstance(node.value, ast_mod.Name) and node.value.id == var_name:
            if node.attr not in seen:
                seen.append(node.attr)
        elif isinstance(node, ast_mod.Subscript) and isinstance(node.value, ast_mod.Name) and node.value.id == var_name:
            sl = node.slice
            if isinstance(sl, ast_mod.Constant) and isinstance(sl.value, str) and sl.value not in seen:
                seen.append(sl.value)
        elif isinstance(node, ast_mod.Call) and isinstance(node.func, ast_mod.Attribute) \
                and isinstance(node.func.value, ast_mod.Name) and node.func.value.id == var_name \
                and node.func.attr in ("get", "pop") and len(node.args) >= 1:
            arg = node.args[0]
            if isinstance(arg, ast_mod.Constant) and isinstance(arg.value, str) and arg.value not in seen:
                seen.append(arg.value)
    return seen


def _collect_return_keys(fn, ast_mod) -> list:
    """启发层：扫描函数体内 return dict 字面量的键。"""
    seen: list = []
    for node in ast_mod.walk(fn):
        if isinstance(node, ast_mod.Return) and isinstance(node.value, ast_mod.Dict):
            for k in node.value.keys:
                if isinstance(k, ast_mod.Constant) and isinstance(k.value, str) and k.value not in seen:
                    seen.append(k.value)
    return seen
