-- =============================================================================
-- ontology_action 挂载对象列改类型：ontology_unique_identifier varchar(255) -> TEXT
-- 背景：行为要能挂多个对象，单值列放不下一组 id，这里改成 TEXT 存逗号分隔的对象id。
-- 口径：创建时选的对象 + 入参来源属性所属对象 + 出参写回对象 + 关系两端，
--       由创建/编辑时重算；固定值、外部输入、仅返回的出参不计入。
-- 存量：此功能未上线，没有按新口径创建过的行为，不需要回填，只改列类型；
--       此前加的 object_unique_identifiers 列（没有数据）一并删掉。
-- 说明：列用 TEXT，与 ontology_action.definition 同口径：对象id 个数随绑定浮动，不封顶。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action
    ALTER COLUMN ontology_unique_identifier TYPE TEXT;

ALTER TABLE ontology.ontology_action
    DROP COLUMN IF EXISTS object_unique_identifiers;

COMMENT ON COLUMN ontology.ontology_action.ontology_unique_identifier IS '行为的挂载对象id，逗号分隔：创建时选的对象 + 入参来源属性所属对象 + 出参写回对象 + 关系两端，由创建/编辑时重算';
