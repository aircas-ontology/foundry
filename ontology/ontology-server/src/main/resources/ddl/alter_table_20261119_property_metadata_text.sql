-- metadata 改为普通字符串存储（TEXT），业务逻辑自行解析
ALTER TABLE "ontology"."ontology_property" ALTER COLUMN metadata TYPE TEXT USING metadata::text;

COMMENT ON COLUMN "ontology"."ontology_property".metadata IS '属性元数据，字符串原样存储（约定为JSON对象字符串），业务逻辑自行解析';