-- =============================================================================
-- action_handle_rule 增加 flow_document 列：前端规则画布文档
-- 背景：规则调度编辑器用画布编排规则，画布文档（节点/连线/布局等任意 JSON）
--       由前端在创建/编辑时传入，后端只存不解析，仅在查看调度详情时原样返回，
--       供前端回显画布。与 judge_json（执行用的定稿规则结构）相互独立。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.action_handle_rule
    ADD COLUMN IF NOT EXISTS flow_document TEXT;

COMMENT ON COLUMN ontology.action_handle_rule.flow_document IS '前端规则画布文档 JSON，仅详情接口回显';