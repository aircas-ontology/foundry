/**
    数据源连接配置表（Agent MCP 数据源扫描工具依赖）
    记录外部数据源（PostgreSQL / MySQL / Oracle 等）的连接信息，
    运行时可依据此表动态建立 JDBC 连接。

    注意：本脚本在 entity_datasource 库（datalake 数据源）执行，
    表位于 db_connection schema 下，即 entity_datasource.db_connection.datasource_connection。

 */

-- 全新库首次执行需先建 schema，否则下方 CREATE TABLE 报 schema "db_connection" does not exist
CREATE SCHEMA IF NOT EXISTS db_connection;

CREATE TABLE IF NOT EXISTS db_connection.datasource_connection
(
    id                             bigserial
    constraint pk_ontology_subspace_property_filter
    primary key,
    space_id                       integer      not null,
    ontology_unique_identifier     varchar(255) not null,
    property_unique_identifier     varchar(255) not null,
    source_property_unique_identifier varchar(255),
    filter_op                      varchar(64),
    filter_value                   text,
    data_type                      varchar(64),
    status                         integer      default 1,
    create_time                    timestamp(6) default CURRENT_TIMESTAMP not null,
    update_time                    timestamp(6) default CURRENT_TIMESTAMP not null
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

comment on table ontology.ontology_subspace_property_filter is '子空间属性筛选条件配置';

comment on column ontology.ontology_subspace_property_filter.id is '主键自增';
comment on column ontology.ontology_subspace_property_filter.space_id is '子空间 id（ontology_space.id）';
comment on column ontology.ontology_subspace_property_filter.ontology_unique_identifier is '子空间中新本体的唯一标识';
comment on column ontology.ontology_subspace_property_filter.property_unique_identifier is '子空间中新属性的唯一标识';
comment on column ontology.ontology_subspace_property_filter.source_property_unique_identifier is '源空间中被复制属性的唯一标识（冗余，便于追溯）';
comment on column ontology.ontology_subspace_property_filter.filter_op is '筛选操作符，取值见 QueryOpEnum';
comment on column ontology.ontology_subspace_property_filter.filter_value is '筛选值：单值 op（EQ/LIKE/GT 等）直接存原始值（如 驱逐舰、10），多值 op（IN/BETWEEN）以 JSON 数组存储（如 ["福特","通用"]、[10,20]）；读取时按 filter_op 区分，值类型由 data_type 决定';
comment on column ontology.ontology_subspace_property_filter.data_type is '属性数据类型，取值见 OntologyDataTypeEnum';
comment on column ontology.ontology_subspace_property_filter.status is '软删除状态位，1 有效 0 无效';
comment on column ontology.ontology_subspace_property_filter.create_time is '记录创建时间';
comment on column ontology.ontology_subspace_property_filter.update_time is '记录修改时间';

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
create index if not exists idx_subspace_filter_space
    on ontology.ontology_subspace_property_filter (space_id);
create index if not exists idx_subspace_filter_ontology
    on ontology.ontology_subspace_property_filter (ontology_unique_identifier);
