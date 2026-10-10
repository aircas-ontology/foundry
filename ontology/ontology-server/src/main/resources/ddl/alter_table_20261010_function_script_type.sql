alter table ontology.function
    add column if not exists script_type varchar(32);

update ontology.function
set script_type = 'GROOVY'
where script_type is null;

alter table ontology.function
    alter column script_type set default 'GROOVY';

alter table ontology.function
    alter column script_type set not null;

comment on column ontology.function.script_type is '脚本类型：GROOVY、PYTHON、TYPESCRIPT';
