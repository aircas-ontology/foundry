-- 本体关系(ontology_link_group)新增 api_name 与 comment 字段
alter table ontology.ontology_link_group
    add column if not exists api_name varchar(255);

comment on column ontology.ontology_link_group.api_name is '关系在代码中使用的api名称';

-- comment 为 PostgreSQL/Kingbase 保留字，列名需加双引号
alter table ontology.ontology_link_group
    add column if not exists description varchar(512);

comment on column ontology.ontology_link_group.description is '关系备注/描述';


