/**
    数据源连接配置表
    记录外部数据源（PostgreSQL / MySQL / Oracle 等）的连接信息，
    运行时可依据此表动态建立 JDBC 连接。

    注意：本脚本在 entity_datasource 库（datalake 数据源）执行，
    表位于 db_connection schema 下，即 entity_datasource.db_connection.datasource_connection。

 */

-- 全新库首次执行需先建 schema，否则下方 CREATE TABLE 报 schema "db_connection" does not exist
CREATE SCHEMA IF NOT EXISTS db_connection;

CREATE TABLE IF NOT EXISTS db_connection.datasource_connection
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
    update_time   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP
);



COMMENT ON TABLE  db_connection.datasource_connection IS '数据源连接配置表';
COMMENT ON COLUMN db_connection.datasource_connection.id           IS '主键ID，自增';
COMMENT ON COLUMN db_connection.datasource_connection.name         IS '数据源名称（允许重复）';
COMMENT ON COLUMN db_connection.datasource_connection.db_type      IS '数据库类型：POSTGRESQL / MYSQL / ORACLE / SQLSERVER 等';
COMMENT ON COLUMN db_connection.datasource_connection.driver_class IS 'JDBC 驱动类全限定名，如 org.postgresql.Driver';
COMMENT ON COLUMN db_connection.datasource_connection.jdbc_url     IS '完整 JDBC URL，用于建立连接';
COMMENT ON COLUMN db_connection.datasource_connection.host         IS '主机地址（冗余字段，便于展示与校验）';
COMMENT ON COLUMN db_connection.datasource_connection.port         IS '端口号（冗余字段）';
COMMENT ON COLUMN db_connection.datasource_connection.db_name      IS '数据库名（冗余字段）';
COMMENT ON COLUMN db_connection.datasource_connection.schema_name  IS 'Schema 名（PostgreSQL 等需要）';
COMMENT ON COLUMN db_connection.datasource_connection.username     IS '连接用户名';
COMMENT ON COLUMN db_connection.datasource_connection.password     IS '连接密码（当前明文存储）';
COMMENT ON COLUMN db_connection.datasource_connection.description  IS '数据源描述';
COMMENT ON COLUMN db_connection.datasource_connection.status       IS '状态：1 启用，0 禁用';
COMMENT ON COLUMN db_connection.datasource_connection.create_time  IS '创建时间';
COMMENT ON COLUMN db_connection.datasource_connection.update_time  IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_datasource_connection_db_type
    ON db_connection.datasource_connection (db_type);


-- #############################################################################
-- 【合并自 ontology2.0-sthq 分支】行为分类体系树迁移，与上面的 datasource_connection 无关。
-- 执行库区别（两段各自独立、幂等，需分别在对应库执行）：
--   · 上面 datasource_connection → entity_datasource(datalake) 库
--   · 下面 action_category       → ontology(主库)
-- #############################################################################

-- =============================================================================
-- 行为分类体系树（Action Category Tree）
-- 新增 action_category 表，并为 ontology_action 增加 action_category_id 关联列。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

create table if not exists action_category
(
    id                serial
        constraint pk_action_category
            primary key,
    parent_id         integer default 0 not null,
    path              text,
    name              varchar(255),
    ontology_space_id integer,
    create_time       timestamp(6),
    update_time       timestamp(6),
    constraint uk_action_category_space_id_path
        unique (ontology_space_id, path)
);

comment on table action_category is '行为分类表';

comment on column action_category.id is '主键id';

comment on column action_category.parent_id is '父分类节点，根节点为0';

comment on column action_category.path is '分类路径，以 / 分隔';

comment on column action_category.name is '分类名称';

comment on column action_category.ontology_space_id is '本体空间id';

comment on column action_category.create_time is '创建时间';

comment on column action_category.update_time is '更新时间';

comment on constraint uk_action_category_space_id_path on action_category is '本体空间id+分类path，唯一索引';

-- 行为挂载到行为分类体系树上
alter table ontology_action
    add column if not exists action_category_id integer;

comment on column ontology_action.action_category_id is '所属行为分类id，对应 action_category.id';

create index if not exists ontology_action_action_category_id_idx
    on ontology_action (action_category_id);
