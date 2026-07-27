alter table ontology_property add column property_category_id int4 ;
comment on column ontology_property.property_category_id is '属性分类id';

ALTER TABLE ontology_property ADD COLUMN metadata jsonb;
COMMENT ON COLUMN ontology_property.metadata IS '属性元数据，JSONB格式，树形结构';


CREATE TABLE "ontology"."property_category" (
    "id" serial,
    "parent_id" int4 NOT NULL DEFAULT 0,
    "path" text COLLATE "pg_catalog"."default",
    "name" varchar(255) COLLATE "pg_catalog"."default",
    "ontology_unique_identifier" varchar(255) COLLATE "pg_catalog"."default",
    "create_time" timestamp(6),
    "update_time" timestamp(6),
    CONSTRAINT "property_category_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "uk_ontology_id_path" UNIQUE ("ontology_unique_identifier", "path")
)
;



COMMENT ON COLUMN "ontology"."property_category"."parent_id" IS '父分类节点，根节点为0';

COMMENT ON COLUMN "ontology"."property_category"."path" IS '分类路径';

COMMENT ON COLUMN "ontology"."property_category"."name" IS '分类名称';

COMMENT ON COLUMN "ontology"."property_category"."ontology_unique_identifier" IS '本体唯一标识';

COMMENT ON COLUMN "ontology"."property_category"."create_time" IS '创建时间';

COMMENT ON COLUMN "ontology"."property_category"."update_time" IS '更新时间';

COMMENT ON CONSTRAINT "uk_ontology_id_path" ON "ontology"."property_category" IS '本体id+分类path，唯一索引';

COMMENT ON TABLE "ontology"."property_category" IS '属性分类表';


CREATE TABLE "ontology"."property_metadata_schema" (
   "id" int4 NOT NULL DEFAULT nextval('"ontology".property_metadata_schema_id_seq'::regclass),
   "ontology_unique_identifier" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
   "path" text COLLATE "pg_catalog"."default",
   "parent_id" int4 NOT NULL DEFAULT 0,
   "is_leaf" bool,
   "enum_values" text COLLATE "pg_catalog"."default",
   "name" varchar(255) COLLATE "pg_catalog"."default",
   CONSTRAINT "property_metadata_schema_pkey" PRIMARY KEY ("id"),
   CONSTRAINT "property_metadata_schema_uk_ontology_id_path" UNIQUE ("ontology_unique_identifier", "path")
)
;

ALTER TABLE "ontology"."property_metadata_schema"
    OWNER TO "iecas";

COMMENT ON COLUMN "ontology"."property_metadata_schema"."id" IS '主键id';

COMMENT ON COLUMN "ontology"."property_metadata_schema"."ontology_unique_identifier" IS '本体唯一标识';

COMMENT ON COLUMN "ontology"."property_metadata_schema"."path" IS '元数据完整path，/分割符';

COMMENT ON COLUMN "ontology"."property_metadata_schema"."parent_id" IS '父元数据id，根节点为0';

COMMENT ON COLUMN "ontology"."property_metadata_schema"."is_leaf" IS '是否为叶子节点，叶子节点才赋值';

COMMENT ON COLUMN "ontology"."property_metadata_schema"."enum_values" IS '为叶子结点时的枚举值列表，/分割符';

COMMENT ON COLUMN "ontology"."property_metadata_schema"."name" IS '元数据名称';

COMMENT ON TABLE "ontology"."property_metadata_schema" IS '属性元数据schema表';

