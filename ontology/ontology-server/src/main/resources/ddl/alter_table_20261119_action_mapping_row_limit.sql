-- =============================================================================
-- ontology_action_mapping_in 增加 row_limit 列：List 入参按子表行读取的行数
-- 背景：函数的 List 入参（元素为基础类型或引用类型）可以绑定对象子表的多行数据；
--       原执行侧扩展表固定只读 10 行，前端既不能表达「要多少行」，也看不出被截断。
-- 口径：只对 List 入参的对象属性绑定行使用；为空按默认 10 行（与既有取值口径一致），
--       填了按该行数读取，顺序沿用行主键倒序。同一个 List 入参有多条元素字段绑定行时
--       必须写成同一个行数（创建行为时校验）。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS row_limit INT;

COMMENT ON COLUMN ontology.ontology_action_mapping_in.row_limit IS 'List 入参按子表行读取的行数；为空按默认 10 行，顺序按行主键倒序';
