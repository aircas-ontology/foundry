-- =============================================================================
-- 出参输出方式下沉到每行（ontology2.0-sthq-new）
-- 背景：原先 ontology_action.output_mode 是行为级单选，所有出参统一 RESULT_ONLY 或
--       WRITE_PROPERTY；现改为 ontology_action_mapping_in 每行独立选择，
--       支持同一行为的部分出参写回属性、另一部分仅返回。
-- 同时补齐 mapping_in 表此前漏加的 source_type / source_config 列。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

-- 每行输出方式：RESULT_ONLY 仅返回 / WRITE_PROPERTY 写入属性
ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS output_mode VARCHAR(32);

COMMENT ON COLUMN ontology.ontology_action_mapping_in.output_mode IS '出参行输出方式：RESULT_ONLY / WRITE_PROPERTY，入参行为空';

-- 出参写回目标对象标识（应用行为不绑对象，出参行需要自己指明属性归属哪个对象）
ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS ontology_unique_identifier VARCHAR(255);

COMMENT ON COLUMN ontology.ontology_action_mapping_in.ontology_unique_identifier IS '该行参数关联的对象唯一标识；出参 WRITE_PROPERTY 时必填，入参行可省略';

-- 补建此前漏加的输入来源列
ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32);

COMMENT ON COLUMN ontology.ontology_action_mapping_in.source_type IS '输入来源：PROPERTY / OBJECT / LIST / FIXED / EXTERNAL';

ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS source_config TEXT;

COMMENT ON COLUMN ontology.ontology_action_mapping_in.source_config IS '输入来源配置：固定值存字面量 JSON，List 存属性唯一标识数组';
