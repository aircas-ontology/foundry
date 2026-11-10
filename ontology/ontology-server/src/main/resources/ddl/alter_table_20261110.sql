-- =============================================================================
-- 补齐行为树 / 实例关系两处缺失列
-- 背景：
--   1) ontology_action.definition：行为树整棵树（节点树 + 黑板 + 变量）的 JSON 存这一列。
--      原 alter_table_20261106_behavior_tree.sql 被清空后该列丢失，且未补进任何其它脚本，
--      新库执行完全部 DDL 后，行为树列表/详情/保存会报 column "definition" does not exist。
--   2) ontology_action_link.target_status：实例关系新增/修改操作的目标状态（Status 枚举名）。
--      PO 与读写代码一直存在，但从未有迁移脚本建列，落库报 column "target_status" does not exist。
-- 说明：两列均允许为空——非树行为 definition 为空，非新增/修改的关系行为 target_status 为空。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS definition TEXT;

COMMENT ON COLUMN ontology.ontology_action.definition IS '行为树定义 JSON（节点树 + 黑板 + 变量），仅 action_kind=TREE 有值，行为为空';

ALTER TABLE ontology.ontology_action_link
    ADD COLUMN IF NOT EXISTS target_status VARCHAR(32);

COMMENT ON COLUMN ontology.ontology_action_link.target_status IS '实例关系新增/修改操作的目标状态（Status 枚举名 ENABLE/DISABLE/DELETE），其余操作为空';