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
