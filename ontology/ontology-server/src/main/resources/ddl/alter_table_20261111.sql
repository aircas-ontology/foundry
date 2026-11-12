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

-- -----------------------------------------------------------------------------
-- action_handle_log 增加 execute_result 列：保存行为调度触发执行后的真实执行结果
-- （executeEntityAction 返回的结果 JSON），与 msg（执行日志消息）分开存放。允许为空。
-- -----------------------------------------------------------------------------
ALTER TABLE ontology.action_handle_log
    ADD COLUMN IF NOT EXISTS execute_result TEXT;

COMMENT ON COLUMN ontology.action_handle_log.execute_result IS '执行结果（行为执行返回的结果 JSON）';

-- -----------------------------------------------------------------------------
-- action_handle_task 增加 period_type 列：区分周期调度类型（每天/每周/每月/Cron），
-- 存枚举名 DAILY/WEEKLY/MONTHLY/CRON。允许为空，历史调度为空。
-- -----------------------------------------------------------------------------
ALTER TABLE ontology.action_handle_task
    ADD COLUMN IF NOT EXISTS period_type VARCHAR(32);

COMMENT ON COLUMN ontology.action_handle_task.period_type IS '周期调度类型：DAILY(每天)/WEEKLY(每周)/MONTHLY(每月)/CRON';
