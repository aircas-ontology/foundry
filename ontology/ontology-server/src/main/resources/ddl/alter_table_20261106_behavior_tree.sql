-- =============================================================================
-- 行为树（ontology2.0-sthq-new）
-- 行为树与行为同表，用 action_kind=TREE 判别；整棵树（节点树 + 黑板 + 变量）存 definition 一列
-- JSON，不建节点表和变量表。
-- 树绑定的是已经建好的行为，它自己只读写黑板，不指向任何对象类型，所以归属列必须允许为空。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS definition TEXT;

COMMENT ON COLUMN ontology.ontology_action.definition IS '行为树定义 JSON（节点树 + 黑板 + 变量），仅 action_kind=TREE 有值，行为为空';

-- 20261102 建该列时只有两类，树同表后补上 TREE
COMMENT ON COLUMN ontology.ontology_action.action_kind IS '行为类别：BASE 基础行为 / APPLICATION 应用行为 / TREE 行为树';

-- 树没有所属对象类型；存量行为仍始终写入该列，语义不变
ALTER TABLE ontology.ontology_action
    ALTER COLUMN ontology_unique_identifier DROP NOT NULL;

COMMENT ON COLUMN ontology.ontology_action.ontology_unique_identifier IS '对象id，即该行为的操作范围；行为树为 NULL';

-- 行为列表与分类计数都按 (空间, 类别) 取数，树同表后必须带类别条件
CREATE INDEX IF NOT EXISTS idx_ontology_action_space_kind
    ON ontology.ontology_action (ontology_space_id, action_kind);
