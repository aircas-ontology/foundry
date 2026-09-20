/**
    数据源连接配置表
    记录外部数据源（PostgreSQL / MySQL / Oracle 等）的连接信息，
    运行时可依据此表动态建立 JDBC 连接。

    注意：本脚本在 entity_datasource 库（datalake 数据源）执行，
    表落在默认 public schema 下，即 entity_datasource.public.datasource_connection。
 */
CREATE TABLE IF NOT EXISTS public.datasource_connection
(
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(128)  NOT NULL,
    db_type       VARCHAR(32)   NOT NULL,
    driver_class  VARCHAR(255)  NOT NULL,
    jdbc_url      VARCHAR(1024) NOT NULL,
    host          VARCHAR(255),
    port          INTEGER,
    db_name       VARCHAR(128),
    schema_name   VARCHAR(128),
    username      VARCHAR(128)  NOT NULL,
    password      VARCHAR(255)  NOT NULL,
    description   VARCHAR(512),
    status        SMALLINT      NOT NULL DEFAULT 1,
    create_time   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_datasource_connection_name UNIQUE (name)
    );

COMMENT ON TABLE  public.datasource_connection IS '数据源连接配置表';
COMMENT ON COLUMN public.datasource_connection.id           IS '主键ID，自增';
COMMENT ON COLUMN public.datasource_connection.name         IS '数据源名称，全局唯一';
COMMENT ON COLUMN public.datasource_connection.db_type      IS '数据库类型：POSTGRESQL / MYSQL / ORACLE / SQLSERVER 等';
COMMENT ON COLUMN public.datasource_connection.driver_class IS 'JDBC 驱动类全限定名，如 org.postgresql.Driver';
COMMENT ON COLUMN public.datasource_connection.jdbc_url     IS '完整 JDBC URL，用于建立连接';
COMMENT ON COLUMN public.datasource_connection.host         IS '主机地址（冗余字段，便于展示与校验）';
COMMENT ON COLUMN public.datasource_connection.port         IS '端口号（冗余字段）';
COMMENT ON COLUMN public.datasource_connection.db_name      IS '数据库名（冗余字段）';
COMMENT ON COLUMN public.datasource_connection.schema_name  IS 'Schema 名（PostgreSQL 等需要）';
COMMENT ON COLUMN public.datasource_connection.username     IS '连接用户名';
COMMENT ON COLUMN public.datasource_connection.password     IS '连接密码（当前明文存储）';
COMMENT ON COLUMN public.datasource_connection.description  IS '数据源描述';
COMMENT ON COLUMN public.datasource_connection.status       IS '状态：1 启用，0 禁用';
COMMENT ON COLUMN public.datasource_connection.create_time  IS '创建时间';
COMMENT ON COLUMN public.datasource_connection.update_time  IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_datasource_connection_db_type
    ON public.datasource_connection (db_type);
