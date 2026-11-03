-- 函数算子同步安全扫描：保存每个 api + version 当前代码对应的最后一次有效扫描结果。
-- REJECTED/FAILED 仅用于本次响应和日志，不允许持久化。

-- 1. 增加可回填字段，字段保持可空以兼容存量数据及过渡期写入。
alter table ontology.function
    add column if not exists scan_status varchar(32);

alter table ontology.function
    add column if not exists scan_result jsonb;

-- 2. 仅回填尚无扫描结果的历史行，重复执行不会覆盖后续已经产生的真实扫描结果。
-- 历史数据的扫描结果统一初始化为空字符串。
update ontology.function
set scan_status = 'SKIPPED',
    scan_result = '""'::jsonb
where scan_result is null;

-- 兼容曾只增加字段、尚未完成回填的中间状态。
update ontology.function
set scan_status = 'SKIPPED'
where scan_status is null;

-- 3. 为未显式提供扫描状态的新写入设置默认值，不增加强制非空约束。
alter table ontology.function
    alter column scan_status set default 'SKIPPED';

-- 移除旧版本脚本可能已创建的状态强校验约束。
alter table ontology.function
    drop constraint if exists ck_function_scan_status;

comment on column ontology.function.scan_status is '安全扫描持久化状态：PASSED/SKIPPED，REJECTED/FAILED 不落库';
comment on column ontology.function.scan_result is '当前代码最后一次有效安全扫描结果，固定契约 JSONB；不保存源码片段或原始报告';
