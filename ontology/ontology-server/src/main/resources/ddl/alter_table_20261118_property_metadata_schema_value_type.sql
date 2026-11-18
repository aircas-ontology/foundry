-- 属性元数据 schema 节点取值类型改造：enumValues 仅枚举 → valueType(INPUT/ENUM/RANGE) + value(字符串)
-- 旧列 enum_values 保留不删（回滚旧 jar 仍可读）；存量数据迁移到新列
ALTER TABLE "ontology"."property_metadata_schema" ADD COLUMN IF NOT EXISTS "value_type" VARCHAR(16);
ALTER TABLE "ontology"."property_metadata_schema" ADD COLUMN IF NOT EXISTS "value" TEXT;

COMMENT ON COLUMN "ontology"."property_metadata_schema"."value_type" IS '取值类型：INPUT 输入值 / ENUM 枚举值 / RANGE 范围值';
COMMENT ON COLUMN "ontology"."property_metadata_schema"."value" IS '取值内容字符串：ENUM 逗号分隔候选项，RANGE 形如 min,max，INPUT 可空';

UPDATE "ontology"."property_metadata_schema"
SET "value_type" = 'ENUM', "value" = "enum_values"
WHERE "value_type" IS NULL AND "enum_values" IS NOT NULL AND "enum_values" <> '';

UPDATE "ontology"."property_metadata_schema"
SET "value_type" = 'INPUT'
WHERE "value_type" IS NULL;