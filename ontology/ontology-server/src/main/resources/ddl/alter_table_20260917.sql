-- 关系分类树表（与本体分类表 ontology_category 平行）
create table if not exists ontology.ontology_link_category
(
    id                serial
    primary key,
    parent_id         integer default 0 not null,
    path              text,
    name              varchar(255),
    ontology_space_id integer,
    create_time       timestamp(6),
    update_time       timestamp(6),
    constraint uk_link_space_id_path
    unique (ontology_space_id, path)
    );

comment on table ontology.ontology_link_category is '关系分类表';

comment on column ontology.ontology_link_category.parent_id is '父分类节点，根节点为0';

comment on column ontology.ontology_link_category.path is '分类路径';

comment on column ontology.ontology_link_category.name is '分类名称';

comment on column ontology.ontology_link_category.ontology_space_id is '本体空间id';

comment on column ontology.ontology_link_category.create_time is '创建时间';

comment on column ontology.ontology_link_category.update_time is '更新时间';


-- 关系(ontology_link_group)归属分类：新增 category_id 字段，使关系可挂接到关系分类树
alter table ontology.ontology_link_group
    add column if not exists category_id integer;

comment on column ontology.ontology_link_group.category_id is '关系分类id（ontology_link_category.id）';