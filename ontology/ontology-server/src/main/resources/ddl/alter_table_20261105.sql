-- =============================================================================
-- 应用行为不绑对象：放开 ontology_action.ontology_unique_identifier 的 NOT NULL
-- 背景：应用行为（action_kind=APPLICATION）只归属对象空间、不挂在具体对象上，
--       创建时允许不传 ontologyIdentifier，空间从行为分类反查。
--       基线建表里该列是 NOT NULL，创建这类行为会报
--       null value in column "ontology_unique_identifier" violates not-null constraint。
-- 说明：基础行为仍由代码强制要求传对象标识，不受影响。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action
    ALTER COLUMN ontology_unique_identifier DROP NOT NULL;

COMMENT ON COLUMN ontology.ontology_action.ontology_unique_identifier IS '对象id；应用行为不绑对象时为空';
