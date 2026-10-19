-- 脚本函数参数层级结构：function_param 新增 fields 列，存储子字段的 JSON 序列化结果
-- 仅 CUSTOMIZE 脚本函数的 OBJECT/LIST 类型参数有值；标量参数和其他类型函数为 NULL
alter table function_param
    add column if not exists fields text;

comment on column function_param.fields is '子字段 JSON（参数类型为自定义类时展开嵌套结构，标量参数为 null）';

-- 容器算子（type=CONTAINER）：function 表新增 docker_config 列，仅存接口级配置（选中接口定义）的 JSON；
-- 容器级信息（镜像/接口文档/启动命令/端口）不再存此处，改存 function_container_instance（一容器一行）。
alter table function
    add column if not exists docker_config text;

comment on column function.docker_config is '容器算子接口级配置 JSON（仅选中接口定义），仅 type=CONTAINER 有值；容器算子不参与版本管理，version 恒为 1.0.0';

-- 容器算子关联键：function 新增 container_key 物理列，作为指向 function_container_instance.container_key 的外键。
-- 关系为 一容器 : 多接口算子 = 1:N（设计 C：一个容器算子的多个接口 = 多条 function 共用同一 container_key）。
-- 加此列后两方向均可 where 命中：正向 function→容器直查，反向 容器→名下所有 function 用 where container_key=?。
alter table function
    add column if not exists container_key varchar(64);

comment on column function.container_key is '容器算子关联的运行实例分组键（= function_container_instance.container_key），仅 type=CONTAINER 有值；一容器:多接口算子=1:N';
create index if not exists idx_function_container_key on function (container_key);

-- 容器算子运行实例：一个容器算子（一镜像多接口，设计 C）一行，是容器级信息的唯一权威来源 + 启停运行态载体；启停管理页列表数据源。
-- 多条 function 通过 container_key 关联到本行（唯一索引）；phase 持久化按枚举 name（CREATED/RUNNING/...）。
-- image / interface_doc 为镜像/接口文档完整元信息的 JSON 快照（详情回显与删除清理取此），run_command/host_port/container_port 扁平列供运行期直取。
create table if not exists function_container_instance (
    id                bigserial primary key,
    container_key     varchar(64)  not null,
    ontology_space_id int,
    display_name      varchar(255),
    container_name    varchar(255),
    image             text,
    interface_doc     text,
    run_command       text,
    host_port         int,
    container_port    int,
    phase             varchar(16)  not null default 'CREATED',
    container_id      varchar(128),
    last_action       varchar(16),
    last_action_at    timestamp,
    message           varchar(512),
    create_time       timestamp,
    update_time       timestamp
);
create unique index if not exists uk_container_instance_key on function_container_instance (container_key);
create unique index if not exists uk_container_instance_name on function_container_instance (container_name);

comment on table function_container_instance is '容器算子运行实例：一个容器算子(一镜像多接口,设计C)一行，容器级配置唯一权威来源 + 常驻容器启停状态';
comment on column function_container_instance.container_name is 'docker 容器名：用户 --name 或平台默认 foundry-ctr-<key>，全局唯一，保存时查重';
