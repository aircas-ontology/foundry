-- =============================================================================
-- function_param 增加 type_text 列：参数完整类型文本（如 List<List<Point>>）
-- 背景：函数详情原先用 param_type（枚举）+ type_reference_name 反拼展示文本，非 JDK 元素的
--       泛型会被收敛成裸 java.util.List，导致 List<Point> 与 List<List<Point>> 在详情里同形，
--       前端无法判断该往哪一级加值。
-- 口径：保存时把参数声明的完整类型文本原样落库，详情优先返回它，为空时回退原有拼装逻辑；
--       执行链路仍用 param_type + type_reference_name，不受影响。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.function_param
    ADD COLUMN IF NOT EXISTS type_text TEXT;

COMMENT ON COLUMN ontology.function_param.type_text IS '参数完整类型文本（声明原样，如 List<List<Point>>）；详情展示优先使用，为空回退 param_type + type_reference_name';