-- 主题服务示例表。新业务表追加到本文件或另起增量脚本，不要改已上线表的历史语句。
CREATE SCHEMA IF NOT EXISTS topic;

CREATE TABLE IF NOT EXISTS topic.topic (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

COMMENT ON TABLE topic.topic IS '主题示例，演示 controller/service/mapper 分层';
COMMENT ON COLUMN topic.topic.name IS '名称';
COMMENT ON COLUMN topic.topic.description IS '说明';

CREATE TABLE IF NOT EXISTS topic.dict
(
    id          bigserial    PRIMARY KEY,
    dict_name   varchar(128) NOT NULL,
    group_code  varchar(64)  NOT NULL,
    group_name  varchar(128) NOT NULL,
    status      smallint     DEFAULT 0 NOT NULL,
    creator     varchar(64),
    create_time timestamp(6)
);

COMMENT ON TABLE topic.dict IS '字典';
COMMENT ON COLUMN topic.dict.dict_name IS '字典名称';
COMMENT ON COLUMN topic.dict.group_code IS '字典组编号';
COMMENT ON COLUMN topic.dict.group_name IS '字典组名称';
COMMENT ON COLUMN topic.dict.status IS '字典状态：0正常，1删除';
COMMENT ON COLUMN topic.dict.creator IS '创建人';
COMMENT ON COLUMN topic.dict.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_dict_group_code ON topic.dict (group_code);

CREATE TABLE IF NOT EXISTS topic.task_topic
(
    id              bigserial     PRIMARY KEY,
    task_name       varchar(128)  NOT NULL,
    task_type       varchar(64)   NOT NULL,
    task_region     varchar(128)  NOT NULL,
    region_point    text,
    start_time      timestamp(6)  NOT NULL,
    end_time        timestamp(6)  NOT NULL,
    description     varchar(2000),
    ontology_space  varchar(1024),
    status          smallint      DEFAULT 0 NOT NULL,
    creator         varchar(64),
    create_time     timestamp(6),
    target_count    integer       DEFAULT 0 NOT NULL,
    task_type_stat  text          DEFAULT '[]' NOT NULL,
    event_count     integer       DEFAULT 0 NOT NULL,
    deleted         smallint      DEFAULT 0 NOT NULL
);

COMMENT ON TABLE topic.task_topic IS '任务主题';
COMMENT ON COLUMN topic.task_topic.task_name IS '任务名称';
COMMENT ON COLUMN topic.task_topic.task_type IS '任务类型，由页面传入';
COMMENT ON COLUMN topic.task_topic.task_region IS '任务区域，由页面传入';
COMMENT ON COLUMN topic.task_topic.region_point IS '区域点位，由页面传入';
COMMENT ON COLUMN topic.task_topic.start_time IS '任务开始时间';
COMMENT ON COLUMN topic.task_topic.end_time IS '任务结束时间';
COMMENT ON COLUMN topic.task_topic.description IS '任务描述';
COMMENT ON COLUMN topic.task_topic.ontology_space IS '关联本体空间，多个用逗号分割';
COMMENT ON COLUMN topic.task_topic.status IS '任务状态：0执行中，1执行完成，默认0';
COMMENT ON COLUMN topic.task_topic.creator IS '创建人';
COMMENT ON COLUMN topic.task_topic.create_time IS '创建时间';
COMMENT ON COLUMN topic.task_topic.target_count IS '目标数量';
COMMENT ON COLUMN topic.task_topic.event_count IS '事件数量';
COMMENT ON COLUMN topic.task_topic.deleted IS '是否删除：0未删除，1已删除';

CREATE INDEX IF NOT EXISTS idx_task_topic_query ON topic.task_topic (task_type, task_region, start_time);

-- 已建表时补数量字段。创建不写入，编辑时更新目标数量、任务类型统计和事件数量。
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS target_count integer DEFAULT 0 NOT NULL;
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS event_count integer DEFAULT 0 NOT NULL;
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS task_type_stat text DEFAULT '[]' NOT NULL;
ALTER TABLE topic.task_topic DROP COLUMN IF EXISTS sea_count;
ALTER TABLE topic.task_topic DROP COLUMN IF EXISTS air_count;
ALTER TABLE topic.task_topic DROP COLUMN IF EXISTS land_count;
COMMENT ON COLUMN topic.task_topic.task_type_stat IS '任务类型统计，如[{"name":"海上","count":1}]';
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS deleted smallint DEFAULT 0 NOT NULL;

CREATE TABLE IF NOT EXISTS topic.event_stage
(
    id          bigserial    PRIMARY KEY,
    task_code   bigint       NOT NULL,
    stage_name  varchar(128) NOT NULL,
    start_time  timestamp(6) NOT NULL,
    end_time    timestamp(6) NOT NULL,
    event_count integer      DEFAULT 0 NOT NULL,
    event_codes varchar(1024),
    creator     varchar(64),
    create_time timestamp(6),
    deleted     smallint     DEFAULT 0 NOT NULL
);

COMMENT ON TABLE topic.event_stage IS '事件阶段';
COMMENT ON COLUMN topic.event_stage.task_code IS '任务编号，对应任务主题 id';
COMMENT ON COLUMN topic.event_stage.stage_name IS '事件阶段名称';
COMMENT ON COLUMN topic.event_stage.start_time IS '事件阶段开始时间';
COMMENT ON COLUMN topic.event_stage.end_time IS '事件阶段结束时间';
COMMENT ON COLUMN topic.event_stage.event_count IS '事件数量';
COMMENT ON COLUMN topic.event_stage.event_codes IS '事件编号，多个用逗号分割';
COMMENT ON COLUMN topic.event_stage.creator IS '创建人';
COMMENT ON COLUMN topic.event_stage.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_event_stage_task_code ON topic.event_stage (task_code);

ALTER TABLE topic.event_stage ADD COLUMN IF NOT EXISTS event_codes varchar(1024);
ALTER TABLE topic.event_stage ADD COLUMN IF NOT EXISTS deleted smallint DEFAULT 0 NOT NULL;
COMMENT ON COLUMN topic.event_stage.deleted IS '是否删除：0未删除，1已删除';



CREATE TABLE IF NOT EXISTS topic.event_topic
(
    id               bigserial    PRIMARY KEY,
    event_name       varchar(128) NOT NULL,
    category         varchar(64)  NOT NULL,
    start_time       timestamp(6) NOT NULL,
    end_time         timestamp(6) NOT NULL,
    geo_region       varchar(128) NOT NULL,
    region_point     text,
    description      varchar(2000),
    ontology_space   varchar(1024),
    status           smallint     DEFAULT 0 NOT NULL,
    deleted          smallint     DEFAULT 0 NOT NULL,
    creator          varchar(64),
    create_time      timestamp(6)
);

COMMENT ON TABLE topic.event_topic IS '事件主题';
COMMENT ON COLUMN topic.event_topic.event_name IS '事件名称';
COMMENT ON COLUMN topic.event_topic.category IS '事件主题类别';
COMMENT ON COLUMN topic.event_topic.start_time IS '开始时间，由页面传入';
COMMENT ON COLUMN topic.event_topic.end_time IS '结束时间，由页面传入';
COMMENT ON COLUMN topic.event_topic.geo_region IS '地理区域，由页面传入';
COMMENT ON COLUMN topic.event_topic.region_point IS '区域点，由页面传入';
COMMENT ON COLUMN topic.event_topic.description IS '事件主题描述';
COMMENT ON COLUMN topic.event_topic.ontology_space IS '关联本体空间，多个用逗号分割';
COMMENT ON COLUMN topic.event_topic.status IS '状态：0执行中，1执行成功，2执行失败，默认0';
COMMENT ON COLUMN topic.event_topic.deleted IS '是否删除：0未删除，1已删除，默认0';
COMMENT ON COLUMN topic.event_topic.creator IS '创建人';
COMMENT ON COLUMN topic.event_topic.create_time IS '创建时间';

CREATE TABLE IF NOT EXISTS topic.event_topic_overview
(
    id              bigserial      PRIMARY KEY,
    event_topic_id  bigint         NOT NULL,
    type            varchar(64)    NOT NULL,
    name            varchar(128)   NOT NULL,
    proportion      numeric(12, 4) NOT NULL,
    quantity        integer        NOT NULL,
    creator         varchar(64),
    create_time     timestamp(6)
);

COMMENT ON TABLE topic.event_topic_overview IS '事件主题总览统计';
COMMENT ON COLUMN topic.event_topic_overview.id IS '编号，自增';
COMMENT ON COLUMN topic.event_topic_overview.event_topic_id IS '事件主题编号';
COMMENT ON COLUMN topic.event_topic_overview.type IS '类型';
COMMENT ON COLUMN topic.event_topic_overview.name IS '名称';
COMMENT ON COLUMN topic.event_topic_overview.proportion IS '占比';
COMMENT ON COLUMN topic.event_topic_overview.quantity IS '数量';
COMMENT ON COLUMN topic.event_topic_overview.creator IS '创建人';
COMMENT ON COLUMN topic.event_topic_overview.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_event_topic_overview_event_topic_id ON topic.event_topic_overview (event_topic_id);

CREATE TABLE IF NOT EXISTS topic.implicit_relation
(
    id               bigserial    PRIMARY KEY,
    start_node_code  varchar(64)  NOT NULL,
    start_node_name  varchar(128) NOT NULL,
    belong_space     varchar(128) NOT NULL,
    relation_type    varchar(64)  NOT NULL,
    hop_count        integer      NOT NULL,
    implicit_count   integer      DEFAULT 0 NOT NULL,
    progress_status  smallint     DEFAULT 0 NOT NULL,
    creator          varchar(64),
    create_time      timestamp(6),
    updater          varchar(64),
    update_time      timestamp(6)
);

COMMENT ON TABLE topic.implicit_relation IS '隐含关系';
COMMENT ON COLUMN topic.implicit_relation.id IS '编号，自增';
COMMENT ON COLUMN topic.implicit_relation.start_node_code IS '起始节点编号';
COMMENT ON COLUMN topic.implicit_relation.start_node_name IS '起始节点名称';
COMMENT ON COLUMN topic.implicit_relation.belong_space IS '所属空间';
COMMENT ON COLUMN topic.implicit_relation.relation_type IS '关联类型，由页面传入';
COMMENT ON COLUMN topic.implicit_relation.hop_count IS '关系跳数，由页面传入';
COMMENT ON COLUMN topic.implicit_relation.implicit_count IS '隐含关系数';
COMMENT ON COLUMN topic.implicit_relation.progress_status IS '进度状态：0未发现，1待校验，2已完成';
COMMENT ON COLUMN topic.implicit_relation.creator IS '创建人，默认admin';
COMMENT ON COLUMN topic.implicit_relation.create_time IS '创建时间';
COMMENT ON COLUMN topic.implicit_relation.updater IS '更新人，默认admin';
COMMENT ON COLUMN topic.implicit_relation.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_implicit_relation_start_node_code ON topic.implicit_relation (start_node_code);

CREATE TABLE IF NOT EXISTS topic.implicit_relation_overview
(
    id                   bigserial    PRIMARY KEY,
    implicit_relation_id bigint       NOT NULL,
    relation_type        varchar(64)  NOT NULL,
    name                 varchar(128) NOT NULL,
    quantity             integer      NOT NULL,
    creator              varchar(64),
    create_time          timestamp(6)
);

COMMENT ON TABLE topic.implicit_relation_overview IS '隐含关系总览统计';
COMMENT ON COLUMN topic.implicit_relation_overview.id IS '编号，自增';
COMMENT ON COLUMN topic.implicit_relation_overview.implicit_relation_id IS '隐含关系编号';
COMMENT ON COLUMN topic.implicit_relation_overview.creator IS '创建人';
COMMENT ON COLUMN topic.implicit_relation_overview.create_time IS '创建时间';

ALTER TABLE topic.implicit_relation_overview ADD COLUMN IF NOT EXISTS relation_type varchar(64) DEFAULT '' NOT NULL;
ALTER TABLE topic.implicit_relation_overview ADD COLUMN IF NOT EXISTS name varchar(128) DEFAULT '' NOT NULL;
ALTER TABLE topic.implicit_relation_overview ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 0 NOT NULL;
ALTER TABLE topic.implicit_relation_overview DROP COLUMN IF EXISTS found_task_count;
ALTER TABLE topic.implicit_relation_overview DROP COLUMN IF EXISTS found_relation_count;
ALTER TABLE topic.implicit_relation_overview DROP COLUMN IF EXISTS pending_relation_count;
ALTER TABLE topic.implicit_relation_overview DROP COLUMN IF EXISTS saved_relation_count;
ALTER TABLE topic.implicit_relation_overview DROP COLUMN IF EXISTS ignored_relation_count;
COMMENT ON COLUMN topic.implicit_relation_overview.relation_type IS '关联类型';
COMMENT ON COLUMN topic.implicit_relation_overview.name IS '名称';
COMMENT ON COLUMN topic.implicit_relation_overview.quantity IS '数量';

CREATE INDEX IF NOT EXISTS idx_implicit_relation_overview_relation_id
    ON topic.implicit_relation_overview (implicit_relation_id);

CREATE TABLE IF NOT EXISTS topic.upload_file
(
    id                   bigserial    PRIMARY KEY,
    file_name            varchar(256) NOT NULL,
    file_type            varchar(64)  NOT NULL,
    ontology_space       varchar(1024),
    ontology_space_name  varchar(1024),
    upload_path          varchar(1024),
    extract_progress     integer      DEFAULT 0 NOT NULL,
    uploader             varchar(64),
    extract_time         timestamp(6),
    deleted              smallint     DEFAULT 0 NOT NULL,
    create_time          timestamp(6)
);

COMMENT ON TABLE topic.upload_file IS '上传文件';
COMMENT ON COLUMN topic.upload_file.id IS '文件编号，自增';
COMMENT ON COLUMN topic.upload_file.file_name IS '文件名称';
COMMENT ON COLUMN topic.upload_file.file_type IS '文件类型';
COMMENT ON COLUMN topic.upload_file.ontology_space IS '关联本体空间编号，多个用逗号分割';
COMMENT ON COLUMN topic.upload_file.ontology_space_name IS '关联本体空间名称';
COMMENT ON COLUMN topic.upload_file.upload_path IS '文件上传路径，网盘上传后续补充';
COMMENT ON COLUMN topic.upload_file.extract_progress IS '抽取进度';
COMMENT ON COLUMN topic.upload_file.uploader IS '上传人员，由页面传入，默认admin';
COMMENT ON COLUMN topic.upload_file.extract_time IS '抽取时间';
COMMENT ON COLUMN topic.upload_file.deleted IS '是否删除：0未删除，1已删除';
COMMENT ON COLUMN topic.upload_file.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_upload_file_name ON topic.upload_file (file_name);


