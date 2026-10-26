-- 容器算子唯一性改按节点维度：同一 node_ip 下 host_port / container_name 不得重复。
-- 多节点部署下端口与容器名各自隔离（每台机器各有独立 docker daemon），
-- 单节点部署 node_ip 为空，COALESCE 归一为空串，保证两种场景都能正确去重。
drop index if exists uk_container_instance_name;
create unique index if not exists uk_ctr_instance_node_name
    on function_container_instance (coalesce(node_ip, ''), container_name);
create unique index if not exists uk_ctr_instance_node_port
    on function_container_instance (coalesce(node_ip, ''), host_port);

-- 心跳监测：TCP 探活结果与最后探测时间
alter table function_container_instance add column if not exists heartbeat_status varchar(20);
alter table function_container_instance add column if not exists last_heartbeat_at timestamp;

-- 容器来源：USER（用户创建，可操作）/ SYSTEM（系统预置，只读）
alter table function_container_instance add column if not exists container_source varchar(20) default 'USER';

-- 算子调用与限流配置模板表
create table if not exists function_rate_limit_template (
    id              bigserial primary key,
    name            varchar(50)  not null,
    timeout_ms      int          not null,
    retry_count     int          not null default 0,
    retry_interval_ms int        not null default 0,
    tps_limit       int          not null,
    max_concurrency int          not null,
    enabled         smallint     not null default 1,
    create_time     timestamp    not null default now(),
    update_time     timestamp    not null default now()
);

-- 函数表加限流配置字段：模板 ID + 配置快照 JSON
alter table function add column if not exists rate_limit_template_id bigint;
alter table function add column if not exists rate_limit_config text;

-- 预置三个默认模板
insert into function_rate_limit_template (name, timeout_ms, retry_count, retry_interval_ms, tps_limit, max_concurrency, enabled)
values
('宽松', 30000, 3, 1000, 100, 50, 1),
('标准', 10000, 2, 500, 50, 20, 1),
('严格', 5000, 1, 200, 10, 5, 1)
on conflict do nothing;
