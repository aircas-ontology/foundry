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
    dict_code   varchar(64)  NOT NULL PRIMARY KEY,
    dict_name   varchar(128) NOT NULL,
    group_code  varchar(64)  NOT NULL,
    group_name  varchar(128) NOT NULL,
    status      smallint     DEFAULT 0 NOT NULL,
    creator     varchar(64),
    create_time timestamp(6)
);

COMMENT ON TABLE topic.dict IS '字典';
COMMENT ON COLUMN topic.dict.dict_code IS '字典编号';
COMMENT ON COLUMN topic.dict.dict_name IS '字典名称';
COMMENT ON COLUMN topic.dict.group_code IS '字典组编号';
COMMENT ON COLUMN topic.dict.group_name IS '字典组名称';
COMMENT ON COLUMN topic.dict.status IS '字典状态：0正常，1删除';
COMMENT ON COLUMN topic.dict.creator IS '创建人';
COMMENT ON COLUMN topic.dict.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_dict_group_code ON topic.dict (group_code);

CREATE TABLE IF NOT EXISTS topic.task_topic
(
    task_code       bigserial     PRIMARY KEY,
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
    sea_count       integer       DEFAULT 0 NOT NULL,
    air_count       integer       DEFAULT 0 NOT NULL,
    land_count      integer       DEFAULT 0 NOT NULL,
    event_count     integer       DEFAULT 0 NOT NULL,
    deleted         smallint      DEFAULT 0 NOT NULL
);

COMMENT ON TABLE topic.task_topic IS '任务主题';
COMMENT ON COLUMN topic.task_topic.task_code IS '任务编号，自增';
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
COMMENT ON COLUMN topic.task_topic.sea_count IS '海上数量';
COMMENT ON COLUMN topic.task_topic.air_count IS '空中数量';
COMMENT ON COLUMN topic.task_topic.land_count IS '陆上数量';
COMMENT ON COLUMN topic.task_topic.event_count IS '事件数量';
COMMENT ON COLUMN topic.task_topic.deleted IS '是否删除：0未删除，1已删除';

CREATE INDEX IF NOT EXISTS idx_task_topic_query ON topic.task_topic (task_type, task_region, start_time);

-- 已建表时补数量字段。数量由后续接口计算，创建和编辑不写入。
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS target_count integer DEFAULT 0 NOT NULL;
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS sea_count integer DEFAULT 0 NOT NULL;
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS air_count integer DEFAULT 0 NOT NULL;
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS land_count integer DEFAULT 0 NOT NULL;
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS event_count integer DEFAULT 0 NOT NULL;
ALTER TABLE topic.task_topic ADD COLUMN IF NOT EXISTS deleted smallint DEFAULT 0 NOT NULL;

CREATE TABLE IF NOT EXISTS topic.event_stage
(
    stage_code  varchar(64)  NOT NULL PRIMARY KEY,
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
COMMENT ON COLUMN topic.event_stage.stage_code IS '事件阶段编号';
COMMENT ON COLUMN topic.event_stage.task_code IS '任务编号，与任务主题任务编号类型一致';
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
