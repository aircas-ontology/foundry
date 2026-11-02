-- =============================================================================
-- 行为创建改造（ontology2.0-sthq-new）
-- 为 ontology_action 增加行为类别、基础操作类型、存储分组、输出方式等字段，
-- 支持基础行为（属性值/实例关系/基础算子）与应用行为（绑算子）的分类管理。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

-- 行为类别：BASE 基础行为 / APPLICATION 应用行为
ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS action_kind VARCHAR(32);

COMMENT ON COLUMN ontology.ontology_action.action_kind IS '行为类别：BASE 基础行为 / APPLICATION 应用行为';

-- 基础行为类型：PROPERTY_VALUE 属性值 / LINK 实例关系 / BASIC_OPERATOR 基础算子
ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS subject_type VARCHAR(32);

COMMENT ON COLUMN ontology.ontology_action.subject_type IS '基础行为类型：PROPERTY_VALUE / LINK / BASIC_OPERATOR，应用行为为空';

-- 基础操作：CREATE / UPDATE / DELETE（仅属性值、实例关系两类有值）
ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS action_operation VARCHAR(32);

COMMENT ON COLUMN ontology.ontology_action.action_operation IS '基础操作：CREATE / UPDATE / DELETE，仅属性值、实例关系两类有值';

-- 属性值操作的目标存储分组
ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS storage_group VARCHAR(255);

COMMENT ON COLUMN ontology.ontology_action.storage_group IS '属性值操作的目标存储分组名称';

-- 输出方式：RESULT_ONLY 仅返回结果 / WRITE_PROPERTY 写入对象属性
ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS output_mode VARCHAR(32);

COMMENT ON COLUMN ontology.ontology_action.output_mode IS '应用行为输出方式：RESULT_ONLY / WRITE_PROPERTY，基础行为为空';
