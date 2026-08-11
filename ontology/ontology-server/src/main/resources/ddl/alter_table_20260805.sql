
/**
  删除表ontology_link_group的列
 */
alter table ontology.ontology_link_group
drop column property_unique_identifier_from;

alter table ontology.ontology_link_group
drop column property_unique_identifier_to;

alter table ontology.ontology_link_group
drop column experimental_status;

alter table ontology.ontology_link_group
drop column mapping;

alter table ontology.ontology_link_group
drop column forward_child_link_id;



/**
  本体空间表
 */
CREATE TABLE IF NOT EXISTS ontology.ontology_space (
    id          SERIAL         NOT NULL,
    display_name        VARCHAR(255)      NOT NULL,
    icon        VARCHAR(1024)     DEFAULT NULL,
    api_name    VARCHAR(255)      NOT NULL,
    description    VARCHAR(255)      NOT NULL,
    create_time  TIMESTAMP(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ontology_space PRIMARY KEY (id)
    );

COMMENT ON TABLE  ontology.ontology_space IS '本体空间';
COMMENT ON COLUMN ontology.ontology_space.id          IS '主键ID，自增';
COMMENT ON COLUMN ontology.ontology_space.display_name        IS '显示名称';
COMMENT ON COLUMN ontology.ontology_space.icon        IS '图标URL';
COMMENT ON COLUMN ontology.ontology_space.api_name    IS 'API名称';
COMMENT ON COLUMN ontology.ontology_space.description    IS '空间描述';
COMMENT ON COLUMN ontology.ontology_space.create_time  IS '创建时间';
COMMENT ON COLUMN ontology.ontology_space.update_time  IS '更新时间';

create unique index ontology_space_uk_display_name
    on ontology.ontology_space (display_name);

create unique index ontology_space_uk_api_name
    on ontology.ontology_space (api_name);

/**
   增加本体空间column
 */
alter table ontology.ontology_meta
    add ontology_space_id integer;

comment on column ontology.ontology_meta.ontology_space_id is '本体空间id';

alter table ontology.ontology_meta
    add ontology_category_id integer;

comment on column ontology.ontology_meta.ontology_category_id is '本体分类id';

alter table ontology.ontology_group
    add ontology_space_id integer;

comment on column ontology.ontology_group.ontology_space_id is '本体空间id';


alter table ontology.ontology_link_group
    add ontology_space_id int4;

comment on column ontology.ontology_link_group.ontology_space_id is '本体空间id';



    /**
        用户表
     */
CREATE TABLE IF NOT EXISTS ontology.user (
       id          SERIAL PRIMARY KEY,
       username    VARCHAR(100) NOT NULL UNIQUE,
       password    VARCHAR(256) NOT NULL,          -- 加密存储的密码
       picture      VARCHAR(1024),                  -- 头像URL
       create_time timestamp(6) DEFAULT CURRENT_TIMESTAMP,
       update_time timestamp(6) DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ontology.user IS '用户表';
COMMENT ON COLUMN ontology.user.id IS '主键ID，自增';
COMMENT ON COLUMN ontology.user.username IS '用户名（唯一）';
COMMENT ON COLUMN ontology.user.password IS '加密存储的登录密码';
COMMENT ON COLUMN ontology.user.picture IS '用户头像URL';
COMMENT ON COLUMN ontology.user.create_time IS '创建时间';
COMMENT ON COLUMN ontology.user.update_time IS '更新时间';

/**
        本体分类表
 */
create table IF NOT EXISTS ontology.ontology_category
(
    id                         serial primary key,
    parent_id                  integer default 0 not null,
    path                       text,
    name                       varchar(255),
    ontology_space_id          int4,
    create_time                timestamp(6),
    update_time                timestamp(6),
    constraint uk_ontology_space_id_path
        unique (ontology_space_id, path)
);

comment on table ontology.ontology_category is '本体分类表';

comment on column ontology.ontology_category.parent_id is '父分类节点，根节点为0';

comment on column ontology.ontology_category.path is '分类路径';

comment on column ontology.ontology_category.name is '分类名称';

comment on column ontology.ontology_category.ontology_space_id is '本体空间id';

comment on column ontology.ontology_category.create_time is '创建时间';

comment on column ontology.ontology_category.update_time is '更新时间';

comment on constraint uk_ontology_space_id_path on ontology.ontology_category is '本体空间id+分类path，唯一索引';

/**
        用户自定义画布表
 */
CREATE TABLE IF NOT EXISTS ontology.user_workshop (
                                        id                SERIAL PRIMARY KEY,
                                        user_id           INT4,
                                        ontology_unique_identifier VARCHAR(255) ,
                                        canvas_config     JSONB ,     -- 画布配置信息（JSON格式）
                                        status            SMALLINT ,               -- 状态：发布，草稿，其他自定义
                                        version           int4,                -- 版本号，初始为1
                                        create_time       TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
                                        update_time       TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ontology.user_workshop IS '用户自定义画布表';
COMMENT ON COLUMN ontology.user_workshop.id IS '主键ID，自增';
COMMENT ON COLUMN ontology.user_workshop.user_id IS '用户ID，关联users表';
COMMENT ON COLUMN ontology.user_workshop.ontology_unique_identifier IS '本体唯一标识符，对应ontology_meta.unique_identifier';
COMMENT ON COLUMN ontology.user_workshop.canvas_config IS '画布配置信息，JSON格式';
COMMENT ON COLUMN ontology.user_workshop.status IS '状态：已发布，草稿';
COMMENT ON COLUMN ontology.user_workshop.version IS '版本号，每次更新递增';
COMMENT ON COLUMN ontology.user_workshop.create_time IS '创建时间';
COMMENT ON COLUMN ontology.user_workshop.update_time IS '更新时间';


         /**
    索引
      */
alter table ontology.ontology_meta
drop constraint unique_display_name;

alter table ontology.ontology_meta
drop constraint unique_api_name;

create unique index ontology_meta_unique_identifier_uindex
    on ontology.ontology_meta (unique_identifier);

create index ontology_meta_uk_spaceid_apiname
    on ontology.ontology_meta (ontology_space_id, api_name);

comment on index ontology.ontology_meta_uk_spaceid_apiname is '空间+api名称唯一索引';

create unique index ontology_meta_uk_spaceid_displayname
    on ontology.ontology_meta (ontology_space_id, display_name);

comment on index ontology.ontology_meta_uk_spaceid_displayname is '空间+显示名称唯一索引';


create unique index ontology_property_uk_ontologyid_displayname
    on ontology.ontology_property (ontology_unique_identifier, display_name);

create unique index ontology_property_uk_ontologyid_apiname
    on ontology.ontology_property (ontology_unique_identifier, api_name);

create unique index ontology_property_unique_identifier_uindex
    on ontology.ontology_property (unique_identifier);