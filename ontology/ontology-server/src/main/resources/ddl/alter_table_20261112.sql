-- =============================================================================
-- 新建实例 URL 表 ontology.ontology_instance_url
-- 背景：领域空间下的实例需要挂一个外部可跳转的 URL，由调用方按（空间 + 对象 + 实例）写入。
--       一个实例至多一条记录：再次提交覆盖 url，并把 create_time（存入时间）刷成本次写入时间。
-- 说明：对象 id 存 ontology_meta.unique_identifier；实例主键在不同对象表里可能是数字也可能是
--       字符串，统一按字符串存 instance_id。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

CREATE TABLE IF NOT EXISTS ontology.ontology_instance_url
(
    id                         BIGSERIAL PRIMARY KEY,
    ontology_space_id          INTEGER       NOT NULL,
    ontology_unique_identifier VARCHAR(255)  NOT NULL,
    instance_id                VARCHAR(255)  NOT NULL,
    url                        VARCHAR(1024) NOT NULL,
    create_time                TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ontology.ontology_instance_url IS '领域空间实例 URL';

COMMENT ON COLUMN ontology.ontology_instance_url.id                         IS '主键ID，自增';
COMMENT ON COLUMN ontology.ontology_instance_url.ontology_space_id          IS '所属对象空间 id';
COMMENT ON COLUMN ontology.ontology_instance_url.ontology_unique_identifier IS '对象唯一标识，对应 ontology_meta.unique_identifier';
COMMENT ON COLUMN ontology.ontology_instance_url.instance_id                IS '实例 id（实例主键值，数字主键按字符串存）';
COMMENT ON COLUMN ontology.ontology_instance_url.url                        IS '实例对应的 URL';
COMMENT ON COLUMN ontology.ontology_instance_url.create_time                IS '存入时间；同一实例再次提交时刷新为本次写入时间';

-- upsert 的冲突目标，缺了它 ON CONFLICT 会直接报错
CREATE UNIQUE INDEX IF NOT EXISTS uk_ontology_instance_url
    ON ontology.ontology_instance_url (ontology_space_id, ontology_unique_identifier, instance_id);
