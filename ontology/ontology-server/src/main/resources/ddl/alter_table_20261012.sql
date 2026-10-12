-- 用户表新增绑定空间字段：配置后该用户查询本体空间列表时仅返回此空间，为空表示不限制
alter table ontology.users
    add column if not exists space_id integer;

comment on column ontology.users.space_id is '绑定的本体空间id（ontology_space.id），为空表示不限制空间';

-- 存量用户配置示例（按需执行，将 username 与 space_id 替换为实际值）
-- update ontology.users set space_id = 1 where username = 'demo';
-- 清空配置（恢复返回全部空间）
-- update ontology.users set space_id = null where username = 'demo';
