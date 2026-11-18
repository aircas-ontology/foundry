-- =============================================================================
-- ontology_action 增加 basic_function_config 列：基础函数行为的查询配置
-- 背景：行为里的「基础算子」改为「基础函数」，不再绑定 BASIC_QUERY 算子，
--       改成直接选对象（可多选，必须同一对象分类）+ 聚合/查询 + 按属性写条件。
-- 口径：这一列存 ActionBasicFunctionConfig 的 JSON（对象列表、操作方式、聚合函数与目标字段、
--       查询字段、过滤条件树），其中的字段一律是属性 apiName 本身，不再有变量名与变量绑定那一层。
--       与 ontology_action.definition 同口径用 TEXT：字段个数随所选属性浮动，不封顶。
-- 存量：基础算子行为（subject_type=BASIC_OPERATOR）继续按 function_api + 绑定行执行，本列不回填；
--       新类别只对新创建的行为写入。
-- 说明：subject_type 列的注释补上 BASIC_FUNCTION，功能口径见 ActionSubjectTypeEnum。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS basic_function_config TEXT;

COMMENT ON COLUMN ontology.ontology_action.basic_function_config IS '基础函数行为的查询配置 JSON（对象列表、聚合/查询、聚合目标与查询字段的属性 apiName、过滤条件树），仅 subject_type=BASIC_FUNCTION 有值';

COMMENT ON COLUMN ontology.ontology_action.subject_type IS '基础行为类型：PROPERTY_VALUE / LINK / BASIC_FUNCTION，应用行为为空；BASIC_OPERATOR 已停止创建和编辑，仅存量行为保留';
