-- =============================================================================
-- action_handle_log 支持手动执行记录：action_handle_type 在 TASK、RULE 之外新增 MANUAL
-- 背景：行为测试、按对象执行、单实体执行、行为树执行与试跑都是人工在页面上发起的，
--       要在同一张执行日志表里留下每次执行的入参、说明与结果，便于按行为回看历史。
-- 口径：MANUAL 行的 action_handle_id 存行为自身的 ontology_action.id（行为树同样是一条行为），
--       TASK、RULE 行仍存各自的调度 id；查询继续走 /action/scheduling/result，
--       按 id + type 成对过滤，两类 id 不会互相串。
-- 说明：列是 varchar(64)，MyBatis 存枚举名，放得下 MANUAL，不改结构，只更新注释。
--       该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

COMMENT ON TABLE ontology.action_handle_log IS '行为执行日志：定时调度、规则调度与手动触发执行';

COMMENT ON COLUMN ontology.action_handle_log.action_handle_type IS 'TASK 定时调度、RULE 规则调度、MANUAL 手动触发执行';

COMMENT ON COLUMN ontology.action_handle_log.action_handle_id IS '执行归属id：TASK/RULE 为调度id，MANUAL 为行为id（ontology_action.id）';
