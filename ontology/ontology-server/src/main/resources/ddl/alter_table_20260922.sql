-- 存储分组表
create sequence if not exists storage_group_id_seq;

create table if not exists ontology.storage_group
(
    id                          serial primary key,
    ontology_unique_identifier  varchar(255) not null,
    storage_name                varchar(255) not null,
    create_time                 timestamp(6),
    update_time                 timestamp(6)
);

comment on table ontology.storage_group is '存储分组表';
comment on column ontology.storage_group.id is '主键';
comment on column ontology.storage_group.ontology_unique_identifier is '本体对象唯一标识';
comment on column ontology.storage_group.storage_name is '存储名称';
comment on column ontology.storage_group.create_time is '创建时间';
comment on column ontology.storage_group.update_time is '更新时间';

create index idx_storage_group_ontology_id on ontology.storage_group (ontology_unique_identifier);
