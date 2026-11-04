CREATE UNIQUE INDEX "uk_function_spaceid_api_version" ON "ontology"."function" USING btree (
    "ontology_space_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
    "api" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "version" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
COMMENT ON INDEX "ontology"."uk_function_spaceid_api_version" IS '空间+api+版本唯一索引';