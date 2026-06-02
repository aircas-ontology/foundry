
ALTER TABLE ontology_meta ADD COLUMN can_generate_entity bool DEFAULT false;
COMMENT ON COLUMN ontology_meta.can_generate_entity is '能否生成实体对象';

ALTER TABLE ontology_property ADD COLUMN default_value VARCHAR(255) ;
COMMENT ON COLUMN ontology_property.default_value is '属性默认值';

ALTER TABLE ontology_property ADD COLUMN storage_group VARCHAR(255) DEFAULT 'main';
COMMENT ON COLUMN ontology_property.storage_group is '属性存储分组，相同分组放在同一张表中';


ALTER TABLE "public"."table_field_mapping"
    ADD CONSTRAINT "uk_source_target_table"
        UNIQUE ("source_table_name", "target_table_name");