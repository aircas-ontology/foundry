-- 函数与本体对象的唯一键收敛到「空间 + 名称」粒度
-- 背景：函数此前是 (api, version) 全局唯一，导致空间1建了 qwe 后空间2无法再用同名 api；
--       本体对象的 (ontology_space_id, api_name) 只是普通索引，缺 DB 级约束。
-- 说明：ontology_space_id 允许为空（历史数据存在无空间记录），Postgres 唯一索引中 NULL 互不相等，
--       因此这些历史行不受新唯一键约束，迁移不会失败。

-- 1. 函数：唯一键 (api, version) → (ontology_space_id, api, version)
drop index if exists ontology.uk_function_api_version;

create unique index if not exists uk_function_spaceid_api_version
    on ontology.function (ontology_space_id, api, version);

comment on index ontology.uk_function_spaceid_api_version is '空间+api+版本唯一索引';

create index if not exists idx_function_ontology_space_id
    on ontology.function (ontology_space_id);

-- 2. 本体对象：(ontology_space_id, api_name) 由普通索引升级为唯一索引
drop index if exists ontology.ontology_meta_uk_spaceid_apiname;

create unique index if not exists ontology_meta_uk_spaceid_apiname
    on ontology.ontology_meta (ontology_space_id, api_name);

comment on index ontology.ontology_meta_uk_spaceid_apiname is '空间+api名称唯一索引';
