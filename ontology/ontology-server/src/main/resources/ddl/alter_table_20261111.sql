-- =============================================================================
-- action_handle_task 增加 entity_json 列
-- 背景：周期调度（TASK）需要携带一段实体执行参数 JSON，定时触发时反序列化成
--       EntityActionExecuteParam，按它指定的实例执行；为空则回退按对象下全部实例遍历。
-- 说明：该列允许为空，非按实例调度的历史记录为空。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.action_handle_task
    ADD COLUMN IF NOT EXISTS entity_json TEXT;

COMMENT ON COLUMN ontology.action_handle_task.entity_json IS '实体执行参数 JSON（EntityActionExecuteParam），定时触发按此执行；为空则按对象下全部实例遍历执行';
