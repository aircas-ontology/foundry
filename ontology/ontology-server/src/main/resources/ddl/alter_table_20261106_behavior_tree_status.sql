-- =============================================================================
-- 行为树下线状态归一
-- 背景：行为树早期的发布/下线接口直接写 1/0，而行为的 status 只有已发布(1)/草稿(2) 两态。
--       现在下线统一写回 2，列表按草稿筛选才能看到已下线的树；存量按 0 下线的树在这里补正。
-- 说明：只改 action_kind=TREE 且 status=0 的行，不影响行为(1/2)与调度状态。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

UPDATE ontology.ontology_action
SET status = 2
WHERE action_kind = 'TREE'
  AND status = 0;

COMMENT ON COLUMN ontology.ontology_action.status IS '行为状态：1 已发布 / 2 草稿；行为树同样两态';
