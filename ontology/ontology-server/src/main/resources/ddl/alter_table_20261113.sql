-- =============================================================================
-- ontology_action_mapping_in.property_unique_identifier 放开非空约束
-- 背景：入参的输入来源支持「固定值 / 外部输入」，这两类取值不来自对象属性，
--       创建/编辑行为时该行的属性标识就是空（见 OntologyActionServiceImpl 的 needProperty 判定），
--       但建表脚本里这一列一直是 not null，保存行为批量插入时直接报
--       null value in column "property_unique_identifier" violates not-null constraint。
-- 口径：属性标识只在 PROPERTY / OBJECT / LIST 来源以及出参 WRITE_PROPERTY 时才必须有值，
--       是否有值由代码校验把关，表上不再强制。
-- 存量：只放开约束，不改列类型、不动数据。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action_mapping_in
    ALTER COLUMN property_unique_identifier DROP NOT NULL;

COMMENT ON COLUMN ontology.ontology_action_mapping_in.property_unique_identifier IS '属性的唯一标识，如果为this, 则表示当前对象；固定值/外部输入来源的入参行没有值';
