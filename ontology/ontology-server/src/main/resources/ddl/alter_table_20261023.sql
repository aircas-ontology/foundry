-- 函数算子同步安全扫描：保存每个 api + version 当前代码对应的最后一次有效扫描结果。
-- REJECTED/FAILED 仅用于本次响应和日志，不允许持久化。

-- 1. 先增加可回填字段。scan_result 此时保持可空，完成历史数据回填后再施加 NOT NULL。
alter table ontology.function
    add column if not exists scan_status varchar(32);

alter table ontology.function
    add column if not exists scan_result jsonb;

-- 2. 仅回填尚无扫描结果的历史行，重复执行不会覆盖后续已经产生的真实扫描结果。
-- 历史脚本从未完成可信扫描，必须标记为 LEGACY_DATA；非脚本算子按策略无需扫描。
update ontology.function
set scan_status = 'SKIPPED',
    scan_result = jsonb_build_object(
            'schemaVersion', 1,
            'status', 'SKIPPED',
            'skipReason', case
                              when type = 'CUSTOMIZE' then 'LEGACY_DATA'
                              else 'NON_SCRIPT_OPERATOR'
                end,
            'codeHash', null,
            'scannedAt', null,
            'scanId', null,
            'durationMs', null,
            'findingsTruncated', null,
            'summary', jsonb_build_object(
                    'total', 0,
                    'high', 0,
                    'medium', 0,
                    'low', 0
                ),
            'findings', jsonb_build_array(),
            'error', null
        )
where scan_result is null;

-- 兼容曾只增加字段、尚未完成回填的中间状态。
update ontology.function
set scan_status = 'SKIPPED'
where scan_status is null;

-- 3. 在施加约束前显式校验，避免带着不完整历史数据继续发布。
do $$
begin
    if exists (
        select 1
        from ontology.function
        where scan_status is null
           or scan_result is null
    ) then
        raise exception 'ontology.function scan fields backfill is incomplete';
    end if;
end
$$;

-- 4. 固化新写入约束。scan_result 不设置通用默认值，所有 insert 必须写入类型匹配的完整结果。
alter table ontology.function
    alter column scan_status set default 'SKIPPED',
    alter column scan_status set not null,
    alter column scan_result set not null;

-- 5. 只允许持久化稳定状态，不允许 REJECTED/FAILED 进入数据库。
do $$
begin
    if exists (
        select 1
        from ontology.function
        where scan_status not in ('PASSED', 'SKIPPED')
    ) then
        raise exception 'ontology.function contains non-persistable scan status';
    end if;
end
$$;

alter table ontology.function
    drop constraint if exists ck_function_scan_status;

alter table ontology.function
    add constraint ck_function_scan_status
        check (scan_status in ('PASSED', 'SKIPPED'));

comment on column ontology.function.scan_status is '安全扫描持久化状态：PASSED/SKIPPED，REJECTED/FAILED 不落库';
comment on column ontology.function.scan_result is '当前代码最后一次有效安全扫描结果，固定契约 JSONB；不保存源码片段或原始报告';
