-- 修改 ontology_space 表的唯一索引
-- 将 api_name 和 display_name 的单列唯一索引改为与 username 的联合唯一索引
-- 这样不同用户可以创建同名空间，但同一用户下空间名必须唯一

-- 1. 删除原有的单列唯一索引
DROP INDEX IF EXISTS ontology_space_uk_apiname;
DROP INDEX IF EXISTS ontology_space_uk_display_name;

-- 2. 创建新的联合唯一索引
-- api_name + username 联合唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS ontology_space_uk_apiname_username
    ON ontology_space (api_name, username);

-- display_name + username 联合唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS ontology_space_uk_displayname_username
    ON ontology_space (display_name, username);

-- 添加索引注释
COMMENT ON INDEX ontology_space_uk_apiname_username IS 'apiName + username 联合唯一键，同一用户下 apiName 不可重复';
COMMENT ON INDEX ontology_space_uk_displayname_username IS 'displayName + username 联合唯一键，同一用户下 displayName 不可重复';
