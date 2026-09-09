alter table ontology.ontology_link_group
    add ontology_space_id integer;

comment on column ontology.ontology_link_group.ontology_space_id is '本体空间id';


alter table ontology.function
    add ontology_space_id integer;

comment on column ontology.function.ontology_space_id is '本体空间id';


alter table ontology.ontology_action
    add ontology_space_id integer;

comment on column ontology.ontology_action.ontology_space_id is '本体空间id';


alter table ontology.action_handle_task
    add ontology_space_id integer;

comment on column ontology.action_handle_task.ontology_space_id is '本体空间id';


alter table ontology.action_handle_rule
    add ontology_space_id integer;

comment on column ontology.action_handle_rule.ontology_space_id is '本体空间id';

alter table ontology.action_handle_task
drop column action_id;

alter table ontology.action_handle_rule
drop column action_id;