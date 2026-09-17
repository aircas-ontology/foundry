alter table ontology.ontology_property
drop column tag;

alter table ontology.ontology_property
drop column secondary_category;

alter table ontology.ontology_property
drop column primary_category;

alter table ontology.ontology_property
    alter column storage_group drop default;

alter table ontology.ontology_property
    alter column datasource_schema drop default;

