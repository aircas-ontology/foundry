-- =============================================================================
-- 应用行为入参支持对象（自定义类）参数逐子字段绑定
-- 背景：算子的对象参数在 function_param 里只有一行，子字段整体存在该行的 fields JSON 中，
--       没有自己的参数 id，因此 ontology_action_mapping_in 无法标识「绑的是哪个子字段」。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

-- 子字段路径：相对所属算子参数，用点分层级，如 position.longitude
ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS param_path VARCHAR(255);

COMMENT ON COLUMN ontology.ontology_action_mapping_in.param_path IS '对象入参的子字段路径（相对该算子参数，点分层级），标量参数或整包取值为空';

-- 实例关系行为的持续时段：新增/修改两类操作按定义把时段写到图库的边上
ALTER TABLE ontology.ontology_action_link
    ADD COLUMN IF NOT EXISTS time_windows TEXT;

COMMENT ON COLUMN ontology.ontology_action_link.time_windows IS '实例关系行为的可见时段 JSON 数组（[{"startTime":..,"endTime":..}]），仅新增/修改两类操作有值，删除及其余行为为空';
