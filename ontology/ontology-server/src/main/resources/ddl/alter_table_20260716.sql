ALTER TABLE ontology_property ADD COLUMN datasource_schema  VARCHAR(255) DEFAULT  'public';
COMMENT ON COLUMN ontology_property.datasource_schema is '数据源schema';

ALTER TABLE ontology_property ADD COLUMN datasource_db VARCHAR(255) DEFAULT 'entity_datasource';
COMMENT ON COLUMN ontology_property.datasource_db is '数据源db';

