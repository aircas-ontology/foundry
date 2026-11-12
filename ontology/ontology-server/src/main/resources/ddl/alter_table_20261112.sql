-- =============================================================================
-- ontology_action 增加 object_unique_identifiers 列
-- 背景：行为到底动了哪些对象，原来要把 ontology_action_mapping_in 的对象列、
--       ontology_action_link 的关系端点和行为自己的挂载对象三处 join 起来才知道。
--       这里在创建/编辑行为时按当次绑定汇总成逗号分隔的对象id 存下来，一次读就能拿到。
-- 口径：入参取的是「来源属性所属对象」（固定值、外部输入没有来源属性，不计入）；
--       出参只计 WRITE_PROPERTY 写回的对象，仅返回结果的出参不落库、不算动了对象；
--       关系取 ontology_link_group 的 from/to 两端；行为自己挂载的对象一并计入。
--       一个都没有时该列为空。
-- 说明：存量行为不回填，重新保存一次才有值；行为树没有三类绑定，它的值是节点绑定的
--       各行为该列的并集，因此树上看到的老行为可能少算。
--       列用 TEXT，与 ontology_action.definition 同口径：对象id 个数随绑定浮动，不封顶。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action
    ADD COLUMN IF NOT EXISTS object_unique_identifiers TEXT;

COMMENT ON COLUMN ontology.ontology_action.object_unique_identifiers IS '行为涉及的对象id，逗号分隔：挂载对象 + 入参来源属性所属对象 + 出参写回对象 + 关系两端，由创建/编辑时重算';
