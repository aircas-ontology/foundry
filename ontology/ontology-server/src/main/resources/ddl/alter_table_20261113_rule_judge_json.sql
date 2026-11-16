-- =============================================================================
-- action_handle_rule 增加 judge_json 列：规则调度定稿结构（唯一规则模式）
-- 背景：规则调度定稿参数为规则数组，每条规则 = 组合条件（judgeParams：左右操作数
--       dynamic/fixed、括号 isLeft/isRight、连接符 linkFlag AND/OR，可整体取反）
--       + 行为执行参数（actionParam：action_api / ontology_identifier /
--       entity_identifiler / entity_json，entity_json 与周期调度同形式）。
--       条件可跨任意对象实例。规则启用时先对绑定实例评估一遍存量数据；此后 CDC
--       只关注插入/更新，条件引用的实例变化且满足条件即触发执行（电平触发）。
--       整段数组存 judge_json，一条调度一行，启停/编辑整体生效。
-- 说明：旧扁平条件（rules/rule_connect_type/entity_json）与逻辑树（logic_json）
--       模式已废弃，代码不再读写；历史遗留列保留不删，避免破坏性变更。
--       监控对象来自条件操作数，不再依赖 ontology_identifier 单对象索引，
--       CDC 按 status 捞开启中的规则后在内存做定向匹配，补一个 status 索引。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.action_handle_rule
    ADD COLUMN IF NOT EXISTS judge_json TEXT;

COMMENT ON COLUMN ontology.action_handle_rule.judge_json IS '规则调度定稿结构 JSON（规则数组：组合条件+行为执行参数）';

CREATE INDEX IF NOT EXISTS idx_handle_rule_status
    ON ontology.action_handle_rule (status);