-- 子空间属性筛选条件表：记录通过向导创建子空间时为每个选中属性配置的筛选条件与属性类型
create table if not exists ontology.ontology_subspace_property_filter
(
    id                             bigserial
    constraint pk_ontology_subspace_property_filter
    primary key,
    space_id                       integer      not null,
    ontology_unique_identifier     varchar(255) not null,
    property_unique_identifier     varchar(255) not null,
    source_property_unique_identifier varchar(255),
    filter_op                      varchar(64),
    filter_value                   text,
    filter_values                  text,
    data_type                      varchar(64),
    status                         integer      default 1,
    create_time                    timestamp(6) default CURRENT_TIMESTAMP not null,
    update_time                    timestamp(6) default CURRENT_TIMESTAMP not null
);

comment on table ontology.ontology_subspace_property_filter is '子空间属性筛选条件配置';

comment on column ontology.ontology_subspace_property_filter.id is '主键自增';
comment on column ontology.ontology_subspace_property_filter.space_id is '子空间 id（ontology_space.id）';
comment on column ontology.ontology_subspace_property_filter.ontology_unique_identifier is '子空间中新本体的唯一标识';
comment on column ontology.ontology_subspace_property_filter.property_unique_identifier is '子空间中新属性的唯一标识';
comment on column ontology.ontology_subspace_property_filter.source_property_unique_identifier is '源空间中被复制属性的唯一标识（冗余，便于追溯）';
comment on column ontology.ontology_subspace_property_filter.filter_op is '筛选操作符，取值见 QueryOpEnum';
comment on column ontology.ontology_subspace_property_filter.filter_value is '单值筛选值（EQ/LIKE/GT 等），JSON 序列化存储';
comment on column ontology.ontology_subspace_property_filter.filter_values is '多值筛选值（IN/BETWEEN 等），JSON 数组序列化存储';
comment on column ontology.ontology_subspace_property_filter.data_type is '属性数据类型，取值见 OntologyDataTypeEnum';
comment on column ontology.ontology_subspace_property_filter.status is '软删除状态位，1 有效 0 无效';
comment on column ontology.ontology_subspace_property_filter.create_time is '记录创建时间';
comment on column ontology.ontology_subspace_property_filter.update_time is '记录修改时间';

create index if not exists idx_subspace_filter_space
    on ontology.ontology_subspace_property_filter (space_id);
create index if not exists idx_subspace_filter_ontology
    on ontology.ontology_subspace_property_filter (ontology_unique_identifier);
