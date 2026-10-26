-- 容器算子唯一性改按节点维度：同一 node_ip 下 host_port / container_name 不得重复。
-- 多节点部署下端口与容器名各自隔离（每台机器各有独立 docker daemon），
-- 单节点部署 node_ip 为空，COALESCE 归一为空串，保证两种场景都能正确去重。
drop index if exists uk_container_instance_name;
create unique index if not exists uk_ctr_instance_node_name
    on function_container_instance (coalesce(node_ip, ''), container_name);
create unique index if not exists uk_ctr_instance_node_port
    on function_container_instance (coalesce(node_ip, ''), host_port);
