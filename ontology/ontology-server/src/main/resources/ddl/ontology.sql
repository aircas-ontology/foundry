-- 切换到 postgres 库（仅 psql 有效；其他客户端请手动选择 postgres 后再执行）
\c postgres

CREATE SCHEMA IF NOT EXISTS ontology;

SET search_path TO ontology;

create sequence action_handle_commit_flash_memory_id_seq;

create sequence action_handle_rule_id_seq;

create sequence action_handle_rule_id_seq1;

create sequence action_handle_task_id_seq;

create sequence action_handle_task_id_seq1;

create sequence function_execute_result_id_seq
    maxvalue 2147483647;

create sequence function_id_seq
    start with 1000;

create sequence ontology_action_id_seq;

create sequence ontology_action_mapping_in_id_seq;

create sequence ontology_child_link_sequence;

create sequence ontology_function_id_seq;

create sequence ontology_function_id_sequence;

create sequence ontology_function_mapping_in_sequence;

create sequence ontology_group_id_seq;

create sequence ontology_id_seq;

create sequence ontology_lema_id_seq
    maxvalue 2147483647;

create sequence ontology_link_group_sequence;

create sequence ontology_link_id_seq;

create sequence ontology_property_id_seq;

create sequence ontology_to_group_id_seq;

create table if not exists action_handle_log
(
    id                 bigserial
    primary key,
    msg                text,
    trigger_time       timestamp(6) default CURRENT_TIMESTAMP(6),
    action_handle_id   bigint,
    action_handle_type varchar(64),
    request_param      text,
    complete_time      timestamp(6),
    task_status        varchar(255)
    );

comment on table action_handle_log is '行为调度执行日志';

comment on column action_handle_log.id is '主键id';

comment on column action_handle_log.msg is '日志';

comment on column action_handle_log.trigger_time is '触发时间';

comment on column action_handle_log.action_handle_id is '行为执行Id';

comment on column action_handle_log.action_handle_type is 'TASK、RULE';

comment on column action_handle_log.request_param is '请求参数';

comment on column action_handle_log.complete_time is '完成时间';

comment on column action_handle_log.task_status is '执行状态';

create table if not exists action_handle_rule
(
    status            varchar(16),
    create_time       timestamp(6),
    update_time       timestamp(6),
    rules             text,
    rule_connect_type smallint,
    name              varchar(255),
    description       varchar(255),
    id                bigint default nextval('ontology.action_handle_rule_id_seq1'::regclass) not null
    primary key,
    ontology_space_id integer
    );

comment on column action_handle_rule.status is '状态: START/STOP';

comment on column action_handle_rule.create_time is '创建时间';

comment on column action_handle_rule.update_time is '修改时间';

comment on column action_handle_rule.rules is '判断规则，结构如下：
[{
  "columnName":"",
  "columnValue":"",
  "condition":""
}]';

comment on column action_handle_rule.rule_connect_type is '条件拼接规则：
1：and
2：or';

comment on column action_handle_rule.name is '名称';

comment on column action_handle_rule.description is '描述';

comment on column action_handle_rule.ontology_space_id is '本体空间id';

alter sequence action_handle_rule_id_seq1 owned by action_handle_rule.id;

create table if not exists action_handle_task
(
    status            varchar(16),
    create_time       timestamp(6),
    update_time       timestamp(6),
    cron              varchar(255),
    name              varchar(255),
    description       varchar(255),
    id                bigint default nextval('ontology.action_handle_task_id_seq1'::regclass) not null
    primary key,
    remark            varchar(255),
    ontology_space_id integer
    );

comment on table action_handle_task is '行为定时任务';

comment on column action_handle_task.status is '任务状态：START、STOP';

comment on column action_handle_task.create_time is '创建时间';

comment on column action_handle_task.update_time is '修改时间';

comment on column action_handle_task.cron is '任务cron表达式';

comment on column action_handle_task.name is '名称';

comment on column action_handle_task.description is '描述';

comment on column action_handle_task.id is '主键';

comment on column action_handle_task.remark is '备注信息';

comment on column action_handle_task.ontology_space_id is '本体空间id';

alter sequence action_handle_task_id_seq1 owned by action_handle_task.id;

create table if not exists function
(
    api               varchar(255)                                                          not null
    constraint uk_function_api
    unique,
    description       varchar(25500),
    status            smallint                                                              not null,
    id                bigint default nextval('ontology.ontology_function_id_seq'::regclass) not null
    primary key,
    object_types      varchar(2550),
    update_time       timestamp(6),
    create_time       timestamp(6),
    type              varchar(255),
    reference_name    varchar(512),
    code              text,
    display_name      varchar(255),
    model             varchar(255),
    ontology_space_id integer
    );

comment on table function is '函数';

comment on column function.api is 'api，函数调用时使用，不可后期修改';

comment on column function.description is '函数描述';

comment on column function.id is '唯一主键，snowid';

comment on column function.object_types is '涉及的本体';

comment on column function.update_time is '更新时间';

comment on column function.create_time is '创建时间';

comment on column function.type is 'CUSTOMIZE,EXIST';

comment on column function.reference_name is '函数全限定名称';

comment on column function.code is '函数代码';

comment on column function.display_name is '函数展示名称';

comment on column function.model is '函数模型';

comment on column function.ontology_space_id is '本体空间id';

create table if not exists function_execute_result
(
    id                  integer default nextval('ontology.function_execute_result_id_seq'::regclass) not null
    primary key,
    task_id             varchar(255)                                                                 not null,
    result              text,
    function_api        varchar(255)                                                                 not null,
    action_api          varchar(255),
    action_context_info text,
    create_time         timestamp(6),
    update_time         timestamp(6),
    function_param      text,
    task_status         varchar(255)
    );

comment on table function_execute_result is '异步函数执行结果';

comment on column function_execute_result.task_id is '任务id';

comment on column function_execute_result.result is '函数执行返回结果';

comment on column function_execute_result.function_api is '函数api';

comment on column function_execute_result.action_api is '函数关联的行为api';

comment on column function_execute_result.action_context_info is '行为上下文信息参数';

comment on column function_execute_result.function_param is '函数输入参数';

comment on column function_execute_result.task_status is '任务状态';

alter sequence function_execute_result_id_seq owned by function_execute_result.id;

create unique index if not exists uk_task_id
    on function_execute_result (task_id);

create table if not exists function_param
(
    function_id         bigint        not null,
    param_name          varchar(255)  not null,
    param_type          varchar(1024) not null,
    id                  bigserial
    primary key,
    create_time         timestamp(0),
    update_time         timestamp(0),
    type_reference_name varchar(255),
    category            varchar(32)   not null,
    param_schema        text,
    param_order         integer       not null,
    description         varchar(255)
    );

comment on table function_param is '函数参数';

comment on column function_param.type_reference_name is '参数类型全限定名';

comment on column function_param.category is 'input/output';

comment on column function_param.param_schema is '参数的schema';

comment on column function_param.param_order is '参数顺序';

comment on column function_param.description is '参数描述';

create table if not exists ontology_action
(
    api                        varchar(255)                                                             not null
    constraint unique_api
    unique,
    function_api               varchar(255),
    ontology_unique_identifier varchar(255)                                                             not null,
    id                         bigint      default nextval('ontology.ontology_action_id_seq'::regclass) not null
    constraint ontology_function_pkey
    primary key,
    create_time                timestamp(6),
    update_time                timestamp(6),
    description                varchar(255),
    display_name               varchar(50) default ''::character varying                                not null,
    status                     smallint    default 1,
    handle_type                smallint    default 1,
    icon                       varchar(255),
    ontology_space_id          integer
    );

comment on table ontology_action is '本体行为';

comment on column ontology_action.api is 'api';

comment on column ontology_action.function_api is '函数api';

comment on column ontology_action.ontology_unique_identifier is '本体id';

comment on column ontology_action.id is '主键，snowid';

comment on column ontology_action.create_time is '创建时间';

comment on column ontology_action.update_time is '更新时间';

comment on column ontology_action.description is '描述';

comment on column ontology_action.display_name is '名称';

comment on column ontology_action.status is '状态，0标识删除，1标识可用';

comment on column ontology_action.handle_type is '动作执行类型；
1：定时
2：规则';

comment on column ontology_action.icon is '行为icon url';

comment on column ontology_action.ontology_space_id is '本体空间id';

create table if not exists ontology_action_link
(
    id                              bigserial
    primary key,
    create_time                     timestamp(6),
    update_time                     timestamp(6),
    ontology_action_id              bigint      not null,
    ontology_link_unique_identifier varchar(64) not null,
    ontology_link_param_expression  text
    );

comment on table ontology_action_link is '本体行为关联关系';

comment on column ontology_action_link.ontology_action_id is '行为id';

comment on column ontology_action_link.ontology_link_unique_identifier is '关系id';

comment on column ontology_action_link.ontology_link_param_expression is '行为绑定的关系存在时的参数表达式';

create index if not exists ontology_action_link_ontology_action_id_idx
    on ontology_action_link (ontology_action_id);

create table if not exists ontology_action_mapping_in
(
    ontology_action_id         bigint                                                                         not null,
    parameter_name             varchar(255),
    property_unique_identifier varchar(255)                                                                   not null,
    id                         bigint default nextval('ontology.ontology_action_mapping_in_id_seq'::regclass) not null
    constraint ontology_funciton_mapping_in_pkey
    primary key,
    create_time                timestamp(6),
    update_time                timestamp(6),
    function_param_id          bigint,
    function_param_expression  text
    );

comment on table ontology_action_mapping_in is '本体行为参数';

comment on column ontology_action_mapping_in.ontology_action_id is 'function的名称';

comment on column ontology_action_mapping_in.parameter_name is '参数的名字';

comment on column ontology_action_mapping_in.property_unique_identifier is '属性的唯一标识，如果为this, 则表示当前本体对象。';

comment on column ontology_action_mapping_in.function_param_id is '关联的函数参数id';

comment on column ontology_action_mapping_in.function_param_expression is '关联的函数参数值的json path表达式：例如data.user[0].name';

create table if not exists ontology_action_rule
(
    id                 bigint not null
    primary key,
    ontology_action_id bigint,
    schema             varchar(255),
    "table"            varchar(255),
    fields             varchar(255)[],
    content            varchar(255),
    executor           varchar(255),
    name               varchar(255),
    create_time        timestamp(6),
    update_time        timestamp(6),
    history_value      varchar(255)
    );

comment on column ontology_action_rule.id is '主键自增';

comment on column ontology_action_rule.ontology_action_id is '本体行为主键';

comment on column ontology_action_rule.schema is '数据库模式';

comment on column ontology_action_rule."table" is '数据表';

comment on column ontology_action_rule.fields is '字段列表';

comment on column ontology_action_rule.content is '表达式';

comment on column ontology_action_rule.executor is '执行器';

comment on column ontology_action_rule.name is '规则名称';

comment on column ontology_action_rule.create_time is '创建时间';

comment on column ontology_action_rule.update_time is '更新时间';

comment on column ontology_action_rule.history_value is '历史值';

create table if not exists ontology_group
(
    id                bigint default nextval('ontology.ontology_group_id_seq'::regclass) not null
    primary key,
    status            integer,
    create_time       timestamp(6),
    update_time       timestamp(6),
    group_name        varchar(255),
    group_id          varchar(64)                                                        not null,
    icon              varchar(512),
    description       varchar(512),
    ontology_space_id integer
    );

comment on table ontology_group is '本体所属分组';

comment on column ontology_group.id is '主键自增';

comment on column ontology_group.status is '软删除状态位，1有效，0无效';

comment on column ontology_group.create_time is '记录创建时间';

comment on column ontology_group.update_time is '记录修改时间';

comment on column ontology_group.group_name is '本体分组名称';

comment on column ontology_group.group_id is '本体的组别id';

comment on column ontology_group.icon is '分组icon url';

comment on column ontology_group.description is '描述';

comment on column ontology_group.ontology_space_id is '本体空间id';

create table if not exists ontology_lemma
(
    id                         integer      default nextval('ontology.ontology_lema_id_seq'::regclass) not null
    constraint ontology_lema_pkey
    primary key,
    ontology_unique_identifier varchar(255)                                                            not null,
    title                      varchar(255),
    content                    text,
    parent_id                  integer                                                                 not null,
    type                       varchar(255)                                                            not null,
    order_index                integer                                                                 not null,
    create_time                timestamp(6) default CURRENT_TIMESTAMP,
    update_time                timestamp(6) default CURRENT_TIMESTAMP,
    extra_info                 text
    );

comment on table ontology_lemma is '本体词条';

comment on column ontology_lemma.id is '主键Id';

comment on column ontology_lemma.ontology_unique_identifier is '本体id';

comment on column ontology_lemma.title is '词条title';

comment on column ontology_lemma.content is '词条内容';

comment on column ontology_lemma.parent_id is '父词条id';

comment on column ontology_lemma.type is '词条类型：统计、文字、网页、链接、表格';

comment on column ontology_lemma.order_index is '目录内词条顺序';

comment on column ontology_lemma.create_time is '创建时间';

comment on column ontology_lemma.update_time is '更新时间';

comment on column ontology_lemma.extra_info is '词条其他信息';

alter sequence ontology_lema_id_seq owned by ontology_lemma.id;

create unique index if not exists uk_ontology_parent_order
    on ontology_lemma (ontology_unique_identifier, parent_id, order_index);

create table if not exists ontology_link_group
(
    id                              bigint       default nextval('ontology.ontology_link_group_sequence'::regclass) not null
    primary key,
    status                          integer,
    create_time                     timestamp(0),
    update_time                     timestamp(0),
    ontology_unique_identifier_from varchar(64),
    ontology_unique_identifier_to   varchar(64),
    name                            varchar(255),
    unique_identifier               varchar(255)                                                                    not null,
    type                            varchar(255) default 'OTHER'::character varying,
    ontology_space_id               integer
    );

comment on table ontology_link_group is '本体关系';

comment on column ontology_link_group.id is '主键自增';

comment on column ontology_link_group.status is '软删除状态位，1有效，0无效';

comment on column ontology_link_group.create_time is '记录创建时间';

comment on column ontology_link_group.update_time is '记录修改时间';

comment on column ontology_link_group.ontology_unique_identifier_from is '开始本体id';

comment on column ontology_link_group.ontology_unique_identifier_to is '结束本体id';

comment on column ontology_link_group.name is '本体间关系的名称';

comment on column ontology_link_group.unique_identifier is '唯一的unique identifier';

comment on column ontology_link_group.type is '关系类型：组合关系，其他关系';

comment on column ontology_link_group.ontology_space_id is '本体空间id';

create table if not exists ontology_meta
(
    id                       bigint       default nextval('ontology.ontology_id_seq'::regclass) not null
    constraint ontology_meta_copy1_copy1_pkey
    primary key,
    status                   integer,
    create_time              timestamp(6) default CURRENT_TIMESTAMP,
    update_time              timestamp(6) default CURRENT_TIMESTAMP,
    icon                     text,
    display_name             varchar(255),
    description              varchar(255),
    api_name                 varchar(255),
    unique_identifier        varchar(255)                                                       not null,
    meta_group_id            varchar(1024),
    parent_unique_identifier varchar(255),
    latest_query_time        timestamp(6) default CURRENT_TIMESTAMP,
    can_generate_entity      boolean      default false,
    ontology_space_id        integer,
    ontology_category_id     integer
    );

comment on table ontology_meta is '本体元数据表';

comment on column ontology_meta.id is '主键自增';

comment on column ontology_meta.status is '软删除状态位，1有效，0无效';

comment on column ontology_meta.create_time is '记录创建时间';

comment on column ontology_meta.update_time is '记录修改时间';

comment on column ontology_meta.icon is '图标';

comment on column ontology_meta.display_name is '本体名称';

comment on column ontology_meta.description is '本体描述';

comment on column ontology_meta.api_name is '在代码里用的本体名称';

comment on column ontology_meta.unique_identifier is '唯一id
';

comment on column ontology_meta.meta_group_id is '组别id，对应于ontology_group中的group_id';

comment on column ontology_meta.parent_unique_identifier is '父本体uid';

comment on column ontology_meta.latest_query_time is '最新查看时间';

comment on column ontology_meta.can_generate_entity is '能否生成实体对象';

comment on column ontology_meta.ontology_space_id is '本体空间id';

comment on column ontology_meta.ontology_category_id is '本体分类id';

create index if not exists ontology_meta_uk_spaceid_apiname
    on ontology_meta (ontology_space_id, api_name);

comment on index ontology_meta_uk_spaceid_apiname is '空间+api名称唯一索引';

create unique index if not exists ontology_meta_uk_spaceid_displayname
    on ontology_meta (ontology_space_id, display_name);

comment on index ontology_meta_uk_spaceid_displayname is '空间+显示名称唯一索引';

create unique index if not exists ontology_meta_unique_identifier_uindex
    on ontology_meta (unique_identifier);

create table if not exists ontology_property
(
    id                         bigint       default nextval('ontology.ontology_property_id_seq'::regclass) not null,
    status                     integer      default 1,
    create_time                timestamp(0),
    update_time                timestamp(0),
    datasource_column_name     varchar(255),
    property_type              varchar(255),
    display_name               varchar(255),
    description                varchar(255),
    visibility                 integer      default 1                                                      not null,
    api_name                   varchar(255)                                                                not null,
    is_primary_key             integer                                                                     not null,
    is_title_key               integer                                                                     not null,
    datasource_id              varchar(64),
    unique_identifier          varchar(255)                                                                not null,
    ontology_unique_identifier varchar(255)                                                                not null,
    tag                        varchar(255),
    secondary_category         varchar(255),
    primary_category           varchar(255),
    default_value              varchar(255),
    storage_group              varchar(255) default 'main'::character varying,
    datasource_schema          varchar(255) default 'public'::character varying,
    datasource_db              varchar(255) default 'entity_datasource'::character varying,
    property_category_id       integer,
    metadata                   jsonb
    );

comment on table ontology_property is '本体属性';

comment on column ontology_property.id is '主键自增';

comment on column ontology_property.status is '软删除标志位，1有效 0无效';

comment on column ontology_property.create_time is '记录创建时间';

comment on column ontology_property.update_time is '记录修改时间';

comment on column ontology_property.datasource_column_name is '数据源的字段名称，即映射';

comment on column ontology_property.property_type is '属性基础类型、时间、字符、数值';

comment on column ontology_property.display_name is '属性名称';

comment on column ontology_property.description is '属性描述';

comment on column ontology_property.visibility is '可见性:1 正常、0隐藏';

comment on column ontology_property.api_name is '在代码里用的属性名称、驼峰式';

comment on column ontology_property.is_primary_key is '是否为主键，1是0否';

comment on column ontology_property.is_title_key is '是否为名称键，将该属性作为本体的显示名称，1是0否';

comment on column ontology_property.datasource_id is '数据源ID';

comment on column ontology_property.unique_identifier is '本体属性identifier';

comment on column ontology_property.ontology_unique_identifier is '本体id';

comment on column ontology_property.tag is '属性标签';

comment on column ontology_property.secondary_category is '属性二级分类（待废弃）';

comment on column ontology_property.primary_category is '属性一级分类（待废弃）';

comment on column ontology_property.default_value is '属性默认值';

comment on column ontology_property.storage_group is '属性存储分组，相同分组放在同一张表中';

comment on column ontology_property.datasource_schema is '数据源schema';

comment on column ontology_property.datasource_db is '数据源db';

comment on column ontology_property.property_category_id is '属性分类id';

comment on column ontology_property.metadata is '属性元数据，JSONB格式，树形结构';

create unique index if not exists ontology_property_uk_ontologyid_displayname
    on ontology_property (ontology_unique_identifier, display_name);

create unique index if not exists ontology_property_uk_ontologyid_apiname
    on ontology_property (ontology_unique_identifier, api_name);

create unique index if not exists ontology_property_unique_identifier_uindex
    on ontology_property (unique_identifier);

create table if not exists property_category
(
    id                         serial
    primary key,
    parent_id                  integer default 0 not null,
    path                       text,
    name                       varchar(255),
    ontology_unique_identifier varchar(255),
    create_time                timestamp(6),
    update_time                timestamp(6),
    constraint uk_ontology_id_path
    unique (ontology_unique_identifier, path)
    );

comment on table property_category is '属性分类表';

comment on column property_category.parent_id is '父分类节点，根节点为0';

comment on column property_category.path is '分类路径';

comment on column property_category.name is '分类名称';

comment on column property_category.ontology_unique_identifier is '本体唯一标识';

comment on column property_category.create_time is '创建时间';

comment on column property_category.update_time is '更新时间';

comment on constraint uk_ontology_id_path on property_category is '本体id+分类path，唯一索引';

create table if not exists property_metadata_schema
(
    id                         serial
    primary key,
    ontology_unique_identifier varchar(255)      not null,
    path                       text,
    parent_id                  integer default 0 not null,
    enum_values                text,
    name                       varchar(255),
    constraint property_metadata_schema_uk_ontology_id_path
    unique (ontology_unique_identifier, path)
    );

comment on table property_metadata_schema is '属性元数据schema表';

comment on column property_metadata_schema.id is '主键id';

comment on column property_metadata_schema.ontology_unique_identifier is '本体唯一标识';

comment on column property_metadata_schema.path is '元数据完整path，/分割符';

comment on column property_metadata_schema.parent_id is '父元数据id，根节点为0';

comment on column property_metadata_schema.enum_values is '为叶子结点时的枚举值列表，/分割符';

comment on column property_metadata_schema.name is '元数据名称';

create table if not exists user_workshop
(
    id                         serial
    primary key,
    user_id                    integer,
    ontology_unique_identifier varchar(255),
    canvas_config              jsonb,
    status                     smallint,
    version                    integer,
    create_time                timestamp(6) default CURRENT_TIMESTAMP,
    update_time                timestamp(6) default CURRENT_TIMESTAMP
    );

comment on table user_workshop is '用户自定义画布表';

comment on column user_workshop.id is '主键ID，自增';

comment on column user_workshop.user_id is '用户ID，关联users表';

comment on column user_workshop.ontology_unique_identifier is '本体唯一标识符，对应ontology_meta.unique_identifier';

comment on column user_workshop.canvas_config is '画布配置信息，JSON格式';

comment on column user_workshop.status is '状态：已发布，草稿';

comment on column user_workshop.version is '版本号，每次更新递增';

comment on column user_workshop.create_time is '创建时间';

comment on column user_workshop.update_time is '更新时间';

create table if not exists users
(
    id          serial
    constraint user_pkey
    primary key,
    username    varchar(100) not null
    constraint user_username_key
    unique,
    password    varchar(256) not null,
    picture     varchar(1024),
    create_time timestamp(6) default CURRENT_TIMESTAMP,
    update_time timestamp(6) default CURRENT_TIMESTAMP
    );

comment on table users is '用户表';

comment on column users.id is '主键ID，自增';

comment on column users.username is '用户名（唯一）';

comment on column users.password is '加密存储的登录密码';

comment on column users.picture is '用户头像URL';

comment on column users.create_time is '创建时间';

comment on column users.update_time is '更新时间';

create table if not exists ontology_space
(
    id           serial
    constraint pk_ontology_space
    primary key,
    display_name varchar(255)                            not null,
    icon         varchar(1024) default NULL::character varying,
    api_name     varchar(255)                            not null,
    create_time  timestamp(6)  default CURRENT_TIMESTAMP not null,
    update_time  timestamp(6)  default CURRENT_TIMESTAMP not null,
    description  varchar(255)
    );

comment on table ontology_space is '本体空间';

comment on column ontology_space.id is '主键ID，自增';

comment on column ontology_space.display_name is '名称';

comment on column ontology_space.icon is '图标URL';

comment on column ontology_space.api_name is 'API名称';

comment on column ontology_space.create_time is '创建时间';

comment on column ontology_space.update_time is '更新时间';

comment on column ontology_space.description is '空间描述';

create unique index if not exists ontology_space_uk_apiname
    on ontology_space (api_name);

comment on index ontology_space_uk_apiname is 'apiName 唯一键';

create unique index if not exists ontology_space_uk_display_name
    on ontology_space (display_name);

create table if not exists ontology_category
(
    id                serial
    primary key,
    parent_id         integer default 0 not null,
    path              text,
    name              varchar(255),
    ontology_space_id integer,
    create_time       timestamp(6),
    update_time       timestamp(6),
    constraint uk_ontology_space_id_path
    unique (ontology_space_id, path)
    );

comment on table ontology_category is '本体分类表';

comment on column ontology_category.parent_id is '父分类节点，根节点为0';

comment on column ontology_category.path is '分类路径';

comment on column ontology_category.name is '分类名称';

comment on column ontology_category.ontology_space_id is '本体空间id';

comment on column ontology_category.create_time is '创建时间';

comment on column ontology_category.update_time is '更新时间';

