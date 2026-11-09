-- =============================================================================
-- 行为树冷却节点的执行状态
-- 背景：冷却节点需要记住「上次执行时间」，而行为树运行时只有请求级变量，跨执行不持久。
--       这里按（空间 + 树 api + 节点 + 执行实例主键）保存最近一次执行时间。
-- 说明：实例主键为空时按空串落库，即同一节点在整棵树范围共享冷却；
--       执行实例主键保证批量执行时各实例的冷却互不影响。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

CREATE TABLE IF NOT EXISTS ontology.ontology_behavior_tree_cooldown
(
    id                 BIGSERIAL PRIMARY KEY,
    ontology_space_id  INTEGER      NOT NULL,
    tree_api           VARCHAR(128) NOT NULL,
    node_id            VARCHAR(128) NOT NULL,
    scope_key          VARCHAR(255) NOT NULL DEFAULT '',
    last_run_at        TIMESTAMP    NOT NULL,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ontology.ontology_behavior_tree_cooldown IS '行为树冷却节点最近一次执行时间，按树+节点+执行实例记录';
COMMENT ON COLUMN ontology.ontology_behavior_tree_cooldown.scope_key IS '执行实例主键；为空表示没有实例主键，整棵树共享冷却';
COMMENT ON COLUMN ontology.ontology_behavior_tree_cooldown.last_run_at IS '上次允许执行的时间，冷却判断与本次占位在同一条 upsert 内完成';

CREATE UNIQUE INDEX IF NOT EXISTS uk_behavior_tree_cooldown_scope
    ON ontology.ontology_behavior_tree_cooldown (ontology_space_id, tree_api, node_id, scope_key);
