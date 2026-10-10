-- 函数算子版本管理：新增版本号、发布状态字段；api 唯一约束调整为 (api, version) 联合唯一
-- 版本号格式 x.y.z（如 1.0.0），同一 api 下版本号不可重复且必须大于已有最大版本号

alter table ontology.function
    add column if not exists version varchar(64) not null default '1.0.0';

-- 发布状态：0 未发布（草稿，可修改/删除），1 已发布（锁定，只能复制为新版本后修改）
alter table ontology.function
    add column if not exists publish smallint not null default 1;

comment on column ontology.function.version is '版本号，格式 x.y.z，同一 api 下递增且唯一';

comment on column ontology.function.publish is '发布状态：0 未发布，1 已发布；新增默认未发布，发布/下线接口流转；仅未发布状态可修改、删除';

-- 历史数据均视为已发布的 1.0.0 版本（publish 默认值 1 已覆盖）

-- 原 api 单列唯一约束降级为 (api, version) 联合唯一
alter table ontology.function
    drop constraint if exists uk_function_api;

create unique index if not exists uk_function_api_version
    on ontology.function (api, version);
