-- ontology_space 增加创建用户与类型字段
-- type：0=空间，1=主题；默认 0，存量数据视为空间
alter table ontology.ontology_space
    add username varchar(255) default NULL::character varying;

comment on column ontology.ontology_space.username is '创建该空间/主题的用户名';

alter table ontology.ontology_space
    add type integer default 0 not null;

comment on column ontology.ontology_space.type is '类型：0=空间，1=主题';
