-- =============================================================================
-- ontology_action 增加 property_unique_identifier 列：行为引用到的属性唯一标识
-- 背景：删除属性时按 ontology_action_mapping_in 判断是否被行为引用，但 List 入参的属性
--       存在 source_config 的 JSON 数组里，基础函数配置按属性 apiName 引用，行为树黑板
--       绑定的属性只写在 definition 里；这些引用删除属性时都看不到，属性删掉后行为静默失效。
-- 口径：创建/编辑行为时把本次定义引用到的属性唯一标识汇总落库，逗号分隔：
--       入参来源属性、出参写回属性、List 来源属性、基础函数字段与回写属性、行为树黑板绑定属性。
--       属性唯一标识创建后不变，只记 id 不记名称；对象继续存在 ontology_unique_identifier 列，
--       关系继续存在 ontology_action_link 表，不在这里重复记录。
-- 存量：只按 ontology_action_mapping_in 回填（覆盖应用行为与属性值行为的属性引用）；
--       存量 List 来源、基础函数配置、行为树定义里的引用不回填，下一次编辑行为时补齐。
-- 说明：列用 TEXT，与 ontology_unique_identifier、definition 同口径。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS property_unique_identifier TEXT;

COMMENT ON COLUMN ontology.ontology_action.property_unique_identifier IS '行为引用到的属性唯一标识，逗号分隔：入参来源属性、出参写回属性、List 来源属性、基础函数字段与回写属性、行为树黑板绑定属性，由创建/编辑时重算';

UPDATE ontology.ontology_action a
SET property_unique_identifier = src.property_ids
FROM (
    SELECT ontology_action_id,
           string_agg(DISTINCT property_unique_identifier, ',') AS property_ids
    FROM ontology.ontology_action_mapping_in
    WHERE property_unique_identifier IS NOT NULL
      AND property_unique_identifier <> ''
    GROUP BY ontology_action_id
) src
WHERE a.id = src.ontology_action_id
  AND (a.property_unique_identifier IS NULL
       OR a.property_unique_identifier = '');