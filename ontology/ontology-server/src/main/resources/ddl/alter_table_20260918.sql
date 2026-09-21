-- =============================================================================
-- 行为分类体系树（Action Category Tree）
-- 新增 action_category 表，并为 ontology_action 增加 action_category_id 关联列。
-- 该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

create table if not exists action_category
(
    id                serial
        constraint pk_action_category
            primary key,
    parent_id         integer default 0 not null,
    path              text,
    name              varchar(255),
    ontology_space_id integer,
    create_time       timestamp(6),
    update_time       timestamp(6),
    constraint uk_action_category_space_id_path
        unique (ontology_space_id, path)
);

comment on table action_category is '行为分类表';

comment on column action_category.id is '主键id';

comment on column action_category.parent_id is '父分类节点，根节点为0';

comment on column action_category.path is '分类路径，以 / 分隔';

comment on column action_category.name is '分类名称';

comment on column action_category.ontology_space_id is '本体空间id';

comment on column action_category.create_time is '创建时间';

comment on column action_category.update_time is '更新时间';

comment on constraint uk_action_category_space_id_path on action_category is '本体空间id+分类path，唯一索引';

-- 行为挂载到行为分类体系树上
alter table ontology_action
    add column if not exists action_category_id integer;

comment on column ontology_action.action_category_id is '所属行为分类id，对应 action_category.id';

create index if not exists ontology_action_action_category_id_idx
    on ontology_action (action_category_id);
