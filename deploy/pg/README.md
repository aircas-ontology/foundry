##   启动命令：docker-compose up -d

# postgres 数据库配置
postgresql.conf（默认data目录下）

# iecas 用户开启REPLICATION 权限
    ALTER ROLE iecas WITH REPLICATION;
## 验证权限
    SELECT rolname, rolsuper, rolreplication, rolcanlogin FROM pg_roles WHERE rolname = 'iecas';

# 数据库schema初始化
初始化脚本：./ontology-server/foundry/ontology/ontology-server/src/main/resources/ddl/ontology.sql


# 设置table Replica Identity 为 full，确保捕获所有变更的前后值
## 分别进入db:entity_datasource和postgres 数据库，执行以下SQL：
### 设置所有 table Replica Identity 为 full
    DO $$
    DECLARE r record;
    BEGIN
    FOR r IN
    SELECT n.nspname AS s, c.relname AS t
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind = 'r'
    AND n.nspname NOT IN ('pg_catalog','information_schema','pg_toast')
    AND n.nspname NOT LIKE 'pg\_%'
    AND c.relreplident <> 'f'
    LOOP
    EXECUTE format('ALTER TABLE %I.%I REPLICA IDENTITY FULL;', r.s, r.t);
    END LOOP;
    END
    $$;

### 验证是否设置成功

    SELECT n.nspname  AS schema,
    c.relname  AS table,
    CASE c.relreplident
    WHEN 'd' THEN 'default'
    WHEN 'f' THEN 'full'
    WHEN 'i' THEN 'index'
    WHEN 'n' THEN 'nothing'
    END AS replica_identity
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind = 'r'
    AND n.nspname NOT IN ('pg_catalog','information_schema');

### 进入db:entity_datasource 配置触发器：对于新增 table 自动设置 Replica Identity 为 full

    CREATE OR REPLACE FUNCTION public.set_full_replica_identity()
    RETURNS event_trigger LANGUAGE plpgsql AS $$
    DECLARE obj record;
    BEGIN
    FOR obj IN
    SELECT * FROM pg_event_trigger_ddl_commands()
    WHERE command_tag = 'CREATE TABLE'
    LOOP
    EXECUTE format('ALTER TABLE %s REPLICA IDENTITY FULL;', obj.object_identity);
    END LOOP;
    END
    $$;
    
    CREATE EVENT TRIGGER trg_set_full_replica_on_create
    ON ddl_command_end
    WHEN TAG IN ('CREATE TABLE')
    EXECUTE FUNCTION public.set_full_replica_identity();
