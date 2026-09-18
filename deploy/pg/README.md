##   启动命令：docker-compose up -d

# postgres 数据库配置
postgresql.conf（默认data目录下）

# iecas 用户开启REPLICATION 权限
    ALTER ROLE iecas WITH REPLICATION;
## 验证权限
    SELECT rolname, rolsuper, rolreplication, rolcanlogin FROM pg_roles WHERE rolname = 'iecas';

# 数据库schema初始化
初始化脚本：./ontology-server/foundry/ontology/ontology-server/src/main/resources/ddl/ontology.sql