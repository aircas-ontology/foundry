-- =============================================================================
-- 行为调度表补充 action_api / ontology_identifier 列
-- 背景：alter_table_20260909.sql 删除了 action_handle_task 和 action_handle_rule
--       的 action_id 列，导致调度记录无法关联到行为。
--       改为直接存储 action_api + ontology_identifier，避免跨表 JOIN。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.action_handle_task
    ADD COLUMN IF NOT EXISTS action_api VARCHAR(255);

COMMENT ON COLUMN ontology.action_handle_task.action_api IS '关联行为 api';

ALTER TABLE ontology.action_handle_task
    ADD COLUMN IF NOT EXISTS ontology_identifier VARCHAR(255);

COMMENT ON COLUMN ontology.action_handle_task.ontology_identifier IS '关联对象唯一标识符';

ALTER TABLE ontology.action_handle_rule
    ADD COLUMN IF NOT EXISTS action_api VARCHAR(255);

COMMENT ON COLUMN ontology.action_handle_rule.action_api IS '关联行为 api';

ALTER TABLE ontology.action_handle_rule
    ADD COLUMN IF NOT EXISTS ontology_identifier VARCHAR(255);

COMMENT ON COLUMN ontology.action_handle_rule.ontology_identifier IS '关联对象唯一标识符';
