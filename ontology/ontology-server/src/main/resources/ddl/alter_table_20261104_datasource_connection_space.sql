/*
    数据源连接配置表加 space_id：每个对象空间至多一条数据源记录。

    背景：datasource_connection 原本只存外部库连接信息，schema 由用户在表里填一个固定值；
          现在改为「每个空间一条记录 + schema 在向导里按请求传入」，于是需要 space_id 作为归属键。

    执行位置：主库（PostgreSQL Main，JDBC URL 带 currentSchema=ontology）。

    顺序：
      1) 加列（允许 NULL，便于存量数据保留）
      2) 建唯一索引（PG 的 UNIQUE 允许多行 NULL，所以存量未回填的行不会冲突）
      3) 回填存量数据的 space_id（按业务规则手工 UPDATE，本脚本不代劳）
      4) 回填完成后执行 ALTER COLUMN SET NOT NULL（脚本里已注释掉，手工取消注释执行）

    唯一索引名 uk_datasource_connection_space_id：与主键 id 的自增序列互不干扰，
    后续若需要「同一数据源挂多个空间」再考虑改成普通索引。
*/

ALTER TABLE ontology.datasource_connection
    ADD COLUMN IF NOT EXISTS space_id INTEGER;

COMMENT ON COLUMN ontology.datasource_connection.space_id IS '对象空间 id，每个空间至多一条数据源记录（唯一索引）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_datasource_connection_space_id
    ON ontology.datasource_connection (space_id);

/*
    存量回填示例（按实际业务规则填写）：

        UPDATE ontology.datasource_connection SET space_id = <目标空间 id> WHERE id = <数据源 id>;

    全部回填完后执行：

        ALTER TABLE ontology.datasource_connection ALTER COLUMN space_id SET NOT NULL;
*/
