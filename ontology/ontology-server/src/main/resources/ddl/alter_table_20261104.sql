-- =============================================================================
-- 行为状态改为 草稿 / 已发布 两态
-- 背景：行为原先只有一个「可用/失效」的 status，新建即对外可用，没有发布环节，
--       与算子的 草稿(publish=0)/已发布(publish=1) 对不上。
-- 取值：沿用存量数据的含义，不做迁移 —— 1 仍表示可用，现改称「已发布」；
--       草稿是新增的 2。
--       新建行为由代码显式落 2，本脚本把列默认值一起对齐，避免绕过接口插入时拿到已发布。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.ontology_action
    ALTER COLUMN status SET DEFAULT 2;

COMMENT ON COLUMN ontology.ontology_action.status IS '行为状态：2 草稿（未发布，智能体和调度不可用），1 已发布';


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


        -- 每行输出方式：RESULT_ONLY 仅返回 / WRITE_PROPERTY 写入属性
ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS output_mode VARCHAR(32);

COMMENT ON COLUMN ontology.ontology_action_mapping_in.output_mode IS '出参行输出方式：RESULT_ONLY / WRITE_PROPERTY，入参行为空';

-- 出参写回目标对象标识（应用行为不绑对象，出参行需要自己指明属性归属哪个对象）
ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS ontology_unique_identifier VARCHAR(255);

COMMENT ON COLUMN ontology.ontology_action_mapping_in.ontology_unique_identifier IS '该行参数关联的对象唯一标识；出参 WRITE_PROPERTY 时必填，入参行可省略';

-- 补建此前漏加的输入来源列
ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32);

COMMENT ON COLUMN ontology.ontology_action_mapping_in.source_type IS '输入来源：PROPERTY / OBJECT / LIST / FIXED / EXTERNAL';

ALTER TABLE ontology.ontology_action_mapping_in
    ADD COLUMN IF NOT EXISTS source_config TEXT;

COMMENT ON COLUMN ontology.ontology_action_mapping_in.source_config IS '输入来源配置：固定值存字面量 JSON，List 存属性唯一标识数组';