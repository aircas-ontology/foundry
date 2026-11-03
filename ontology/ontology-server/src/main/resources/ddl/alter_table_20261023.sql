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

-- 容器算子多实例部署：function_container_instance 新增归属节点两列。
-- ontology-server 可能部署在多台机器（各自本地磁盘 + 各自 docker 守护进程，image-dir 不共享），
-- 镜像 tar 只落在收到上传请求那台机器的磁盘上，容器也只能在那台机器的 docker 上起停。
-- node_ip / node_port 记录「谁承载了这个容器」：启停控制请求据此自动转发到归属节点的 java 进程，
-- 执行期访问容器 -p 端口的地址同样取 node_ip（为空回退全局 container-operator.exec-host）。
alter table function_container_instance
    add column if not exists node_ip   varchar(64),
    add column if not exists node_port int;

comment on column function_container_instance.node_ip   is '承载该容器的 ontology-server 实例 IP（镜像 tar 与 docker 容器只在此节点上）；为空视为本节点，兼容单节点部署与历史数据';
comment on column function_container_instance.node_port is '承载该容器的 ontology-server 实例 HTTP 端口（启停/刷新控制请求按 ip:port 转发到此进程）';

-- 容器算子创建向导的文件归属登记表：上传镜像/接口文档只落当前节点的本地磁盘，
-- 而「探查接口」入参仅带 docPath、「创建算子」仅带镜像 path，都无从得知文件在哪台机器上，
-- 故上传时按绝对路径登记归属节点，后续两步据此反查并自动转发；创建成功后删除对应登记行。
create table if not exists function_container_upload (
    id          bigserial primary key,
    file_path   varchar(1024) not null,
    kind        varchar(16)   not null,
    node_ip     varchar(64)   not null,
    node_port   int           not null,
    create_time timestamp
);
create unique index if not exists uk_container_upload_path on function_container_upload (file_path);

comment on table function_container_upload is '容器算子上传文件归属登记：镜像 tar / 接口文档落在上传节点本地磁盘，探查与创建按路径反查归属节点后转发';
comment on column function_container_upload.file_path is '上传后返回给前端的节点本地绝对路径（UUID 前缀，全局唯一）';
comment on column function_container_upload.kind      is '文件类型：image=镜像 tar，doc=OpenAPI 接口文档';
comment on column function_container_upload.node_ip   is '实际保存该文件的 ontology-server 实例 IP';
comment on column function_container_upload.node_port is '该实例的 HTTP 端口';
