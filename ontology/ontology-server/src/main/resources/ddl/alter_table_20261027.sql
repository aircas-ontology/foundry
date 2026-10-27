-- 函数算子代码模板表：按脚本语言维护示例代码，创建脚本算子时选用带出初始代码。
-- language 存 FunctionLanguageEnum 枚举名（GROOVY/JAVA/PYTHON/TYPESCRIPT），与 function.language 口径一致。
SET search_path TO ontology;
create table if not exists function_operator_template (
    id          bigserial primary key,
    name        varchar(50)  not null,
    language    varchar(32)  not null,
    description varchar(500),
    code        text         not null,
    create_time timestamp    not null default now(),
    update_time timestamp    not null default now()
);

create unique index if not exists uk_operator_template_name on function_operator_template (name);

comment on table function_operator_template is '函数算子代码模板：按脚本语言维护示例代码，创建算子时选用';
comment on column function_operator_template.name is '模板名称，全局唯一';
comment on column function_operator_template.language is '脚本语言：GROOVY/JAVA/PYTHON/TYPESCRIPT';
comment on column function_operator_template.description is '模板说明';
comment on column function_operator_template.code is '模板代码内容';
comment on column function_operator_template.create_time is '创建时间';
comment on column function_operator_template.update_time is '更新时间';

-- ============================ 预置代码模板：每种语言 2 条 ============================
-- 代码体按各语言真实执行契约编写（handle 方法名统一，入参绑定规则各语言不同）：
--   GROOVY     进程内 GroovyClassLoader：必须有 handle 方法且返回类型严格为 FunctionResultVO；
--              入参按 function_param 参数表顺序位置传入，形参名不可靠，多入参请写 @FuncParam(name=...)。
--   JAVA       进程内 javac：第一个 class 名即编译类名；单形参时收到的是整个入参 Map（不是某个值），
--              多形参才按形参名从 Map 取值；返回类型不限，但返回 FunctionResultVO 才能被参数解析器识别出 data。
--   PYTHON     远程 runner：必须模块顶层 def handle；单形参收整个 dict，>1 形参按 dict 值顺序展开；
--              只有标准库（无 numpy/pandas），子进程执行超时 5 秒；返回 dict 按 FunctionResultVO 键写，裸值自动包成 {"data": 值}。
--   TYPESCRIPT 远程 runner：必须 export function handle（不 export 取不到）；async 支持；
--              展开规则同 Python；require 只允许 Node 内置模块；返回数组会被包成 {data: [...]}。
-- @result:类型 指令独占一行才生效，可写值：integer/long/short/byte/double/number/float/string/str/boolean/bool/list/array/date/datetime 等，
-- object/map 类不算标量、写了会被忽略；仅当出参只有一个时生效。
-- 注意：代码体内含分号，整段选中执行，别让客户端按分号把一条 insert 拆断。
insert into function_operator_template (name, language, description, code) values
('Groovy 文本转整数模板', 'GROOVY', '单入参：形参用 String，返回类型必须是 FunctionResultVO，否则校验报「函数名称handle不存在或者返回类型错误」。类名随意。',
$groovy$import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO
// @result:integer
class GroovyTextToInteger {

    FunctionResultVO handle(String text) {
        def v = text == null ? "0" : text.toString().trim()
        int n = 0
        try {
            n = Integer.parseInt(v)
        } catch (Exception e) {
            n = 0
        }
        return FunctionResultVO.builder()
                .data(n)
                .description("解析结果：" + n)
                .build()
    }
}
$groovy$),

('Groovy 多入参具名模板', 'GROOVY', '多入参：形参名在 Groovy 字节码里不可靠，用 @FuncParam(name, description) 显式声明参数名（两个属性都必填），值按参数表顺序位置传入。',
$groovy$import com.aircas.ptr.foundry.ontology.aspect.FuncParam
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO
// @result:string
class GroovyJoinText {

    FunctionResultVO handle(@FuncParam(name = "name", description = "用户名") String name,
                            @FuncParam(name = "times", description = "重复次数") Integer times) {
        int n = times == null ? 1 : times
        StringBuilder sb = new StringBuilder()
        for (int i = 0; i < n; i++) {
            sb.append(name == null ? "" : name).append(' ')
        }
        return FunctionResultVO.builder()
                .data(sb.toString().trim())
                .description("name 重复 " + n + " 次")
                .build()
    }
}
$groovy$),

('Java 单入参 Map 模板', 'JAVA', '单形参的 Java 脚本收到的是整个入参 Map，不是某个参数值，所以形参必须声明成 Map 再从 key 取值；需要 JDK 17 启动，且 import 只能引主程序已有类。',
$java$import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;

import java.util.Map;

// @result:string
public class JavaMapInput {

    public FunctionResultVO handle(Map<String, Object> input) {
        Object text = input == null ? null : input.get("text");
        String trimmed = text == null ? "" : text.toString().trim();
        return FunctionResultVO.builder()
                .data(trimmed)
                .description("长度=" + trimmed.length())
                .build();
    }
}
$java$),

('Java 多入参具名模板', 'JAVA', '两个及以上形参时按形参名从入参 Map 取值（编译带 -parameters），形参名要与 function_param 里登记的参数名一致；取不到值时按 Map 值顺序兜底。',
$java$import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;

// @result:string
public class JavaNamedArgs {

    public FunctionResultVO handle(String name, Integer count) {
        int n = count == null ? 0 : count;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(name == null ? "" : name).append(' ');
        }
        return FunctionResultVO.builder()
                .data(sb.toString().trim())
                .description("按形参名取入参：name / count")
                .build();
    }
}
$java$),

('Python 单入参 dict 模板', 'PYTHON', '单形参收到整个入参 dict，从 key 取值；返回 dict 时用 FunctionResultVO 的键（data/description/startTime/endTime/taskId/timeWindows），调用方只拿 data；仅标准库可用，超时 5 秒。',
$python$# @result:string
def handle(input_data: dict) -> dict:
    text = (input_data or {}).get("text")
    s = "" if text is None else str(text).strip()
    return {"data": s.upper(), "description": f"长度={len(s)}"}
$python$),

('Python 多入参求和模板', 'PYTHON', '形参多于 1 个时 runner 按入参值的顺序逐个展开传入，形参顺序要和参数表一致；直接 return 裸值会被自动包成 {"data": 裸值}。',
$python$import datetime

# @result:double
def handle(name: str, score: float, extra: float) -> dict:
    total = (score or 0.0) + (extra or 0.0)
    return {
        "data": round(total, 4),
        "description": f"{name} 合计 {total} @ {datetime.datetime.now()}",
    }
$python$),

('TypeScript 单入参对象模板', 'TYPESCRIPT', 'TS 单形参收到整个入参对象；必须 export，否则 runner 从 module.exports 取不到 handle；只做转义不做类型检查，require 只允许 Node 内置模块。',
$ts$// @result:string
export function handle(input: { text?: string }): { data: string; description: string } {
  const text = input && input.text !== undefined ? String(input.text).trim() : ''
  return { data: text.toUpperCase(), description: `长度=${text.length}` }
}
$ts$),

('TypeScript 异步多入参模板', 'TYPESCRIPT', 'async handle 会被 await（整体超时限制内）；形参多于 1 个时按 Object.values 顺序展开；返回数组会自动包成 {data: [...]}，指令写 list。',
$ts$// @result:list
export async function handle(prefix: string, count: number): Promise<string[]> {
  const n = Number.isFinite(count) ? Math.max(0, Math.floor(count)) : 0
  const items: string[] = []
  for (let i = 0; i < n; i++) {
    items.push(`${prefix ?? ''}-${i + 1}`)
  }
  return items
}
$ts$)
on conflict do nothing;
