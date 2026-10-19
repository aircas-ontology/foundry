-- 脚本函数参数层级结构：function_param 新增 fields 列，存储子字段的 JSON 序列化结果
-- 仅 CUSTOMIZE 脚本函数的 OBJECT/LIST 类型参数有值；标量参数和其他类型函数为 NULL
alter table function_param
    add column if not exists fields text;

comment on column function_param.fields is '子字段 JSON（参数类型为自定义类时展开嵌套结构，标量参数为 null）';

-- 容器算子（type=CONTAINER）：function 表新增 docker_config 列，存储镜像信息 + 接口文档信息 + 选中接口定义的 JSON
alter table function
    add column if not exists docker_config text;

comment on column function.docker_config is '容器算子配置 JSON（镜像/接口文档/选中接口），仅 type=CONTAINER 有值；容器算子不参与版本管理，version 恒为 1.0.0';
