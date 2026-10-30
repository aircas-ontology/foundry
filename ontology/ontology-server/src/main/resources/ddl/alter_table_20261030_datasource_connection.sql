/*
    数据源连接配置表迁库：entity_datasource.db_connection.datasource_connection → ontology.datasource_connection

    背景：该表原本建在数据湖库（datalake 数据源）的 db_connection schema 下，
          但它保存的是「平台级外部数据源连接配置」，与数据湖里按空间动态生成的实体业务表无关，
          归属主库更合理，也让 Agent 数据源扫描工具与其余元数据共用一个库。

    执行位置：主库（PostgreSQL Main，JDBC URL 带 currentSchema=ontology），本脚本显式写 ontology. 前缀。
    代码侧：DatasourceConnection PO 使用裸表名 @TableName("datasource_connection")，
            Mapper 已从 datalakeMapper 挪到 mainMapper，运行时解析为 ontology.datasource_connection。

    注意：跨库（datalake → 主库）不能用 INSERT ... SELECT，数据搬迁见文件末尾「数据搬迁」小节。
*/

CREATE SCHEMA IF NOT EXISTS ontology;

CREATE TABLE IF NOT EXISTS ontology.datasource_connection
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

COMMENT ON TABLE  ontology.datasource_connection IS '数据源连接配置表';
COMMENT ON COLUMN ontology.datasource_connection.id           IS '主键ID，自增';
COMMENT ON COLUMN ontology.datasource_connection.name         IS '数据源名称（允许重复）';
COMMENT ON COLUMN ontology.datasource_connection.db_type      IS '数据库类型：POSTGRESQL / MYSQL / ORACLE / SQLSERVER 等';
COMMENT ON COLUMN ontology.datasource_connection.driver_class IS 'JDBC 驱动类全限定名，如 org.postgresql.Driver';
COMMENT ON COLUMN ontology.datasource_connection.jdbc_url     IS '完整 JDBC URL，用于建立连接';
COMMENT ON COLUMN ontology.datasource_connection.host         IS '主机地址（冗余字段，便于展示与校验）';
COMMENT ON COLUMN ontology.datasource_connection.port         IS '端口号（冗余字段）';
COMMENT ON COLUMN ontology.datasource_connection.db_name      IS '数据库名（冗余字段）';
COMMENT ON COLUMN ontology.datasource_connection.schema_name  IS 'Schema 名（PostgreSQL 等需要）';
COMMENT ON COLUMN ontology.datasource_connection.username     IS '连接用户名';
COMMENT ON COLUMN ontology.datasource_connection.password     IS '连接密码（当前明文存储）';
COMMENT ON COLUMN ontology.datasource_connection.description  IS '数据源描述';
COMMENT ON COLUMN ontology.datasource_connection.status       IS '状态：1 启用，0 禁用';
COMMENT ON COLUMN ontology.datasource_connection.create_time  IS '创建时间';
COMMENT ON COLUMN ontology.datasource_connection.update_time  IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_datasource_connection_db_type
    ON ontology.datasource_connection (db_type);


/*
    ============ 数据搬迁（id 必须原样带上，Agent 会话中的 datasourceId 按 id 引用） ============

    方式一（推荐，行数少时最直接）：在数据湖库执行下面的查询，把结果导出成 INSERT 语句，
    再在主库执行那些 INSERT，最后跑一次 setval 重置序列。

        SELECT id, name, db_type, driver_class, jdbc_url, host, port, db_name, schema_name,
               username, password, description, status, create_time, update_time
        FROM db_connection.datasource_connection;

    方式二（dblink，同实例跨库直搬）：dblink 是 contrib 扩展，是否可用先执行

        SELECT 1 FROM pg_available_extensions WHERE name = 'dblink';   -- 有结果才走这条路径

    在主库执行（host/port/账号密码按实际环境改）：

        CREATE EXTENSION IF NOT EXISTS dblink;
        INSERT INTO ontology.datasource_connection
            (id, name, db_type, driver_class, jdbc_url, host, port, db_name, schema_name,
             username, password, description, status, create_time, update_time)
        SELECT * FROM dblink(
            'host=172.16.18.58 port=35432 dbname=entity_datasource user=postgres password=123456',
            'SELECT id, name, db_type, driver_class, jdbc_url, host, port, db_name, schema_name, '
            || 'username, password, description, status, create_time, update_time '
            || 'FROM db_connection.datasource_connection'
        ) AS t(id INT, name VARCHAR, db_type VARCHAR, driver_class VARCHAR, jdbc_url VARCHAR,
               host VARCHAR, port INT, db_name VARCHAR, schema_name VARCHAR,
               username VARCHAR, password VARCHAR, description VARCHAR,
               status SMALLINT, create_time TIMESTAMP, update_time TIMESTAMP);

    两种方式搬完都要重置序列，否则新增数据源会从 id=1 开始撞主键：

        SELECT setval(pg_get_serial_sequence('ontology.datasource_connection', 'id'),
                      (SELECT COALESCE(MAX(id), 0) + 1 FROM ontology.datasource_connection), false);

    核对无误后再清理旧表（旧库确认后手工执行，不在本脚本内自动跑）：

        -- DROP TABLE IF EXISTS db_connection.datasource_connection;
        -- DROP SCHEMA IF EXISTS db_connection;          -- schema 下若还有其他对象会失败，先别加 CASCADE
*/
