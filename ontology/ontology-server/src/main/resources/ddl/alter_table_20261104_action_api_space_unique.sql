-- =============================================================================
-- 行为 api 改为空间内唯一
-- 背景：行为归属是空间，api 只在空间内唯一；跨空间允许同名 api。
--       原先 `ontology_action.api` 有全局唯一约束 `unique_api`，现改为复合唯一。
-- 步骤：
--   1. 删除全局唯一约束 `unique_api`
--   2. 为存量数据回填 `ontology_space_id`（从关联对象的 `ontology_space_id` 取）
--   3. 新增复合唯一约束 `(api, ontology_space_id)`
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

-- 1. 删除全局唯一约束（如果存在）
ALTER TABLE ontology.ontology_action
    DROP CONSTRAINT IF EXISTS unique_api;

-- 2. 回填存量数据的 ontology_space_id
UPDATE ontology.ontology_action a
SET ontology_space_id = (
    SELECT m.ontology_space_id
    FROM ontology.ontology_meta m
    WHERE m.unique_identifier = a.ontology_unique_identifier
)
WHERE a.ontology_space_id IS NULL;

-- 3. 新增复合唯一约束
ALTER TABLE ontology.ontology_action
    ADD CONSTRAINT unique_api_space UNIQUE (api, ontology_space_id);

COMMENT ON CONSTRAINT unique_api_space ON ontology.ontology_action IS '行为 api 在空间内唯一';
