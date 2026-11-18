-- =============================================================================
-- action_handle_log 记录执行机器与执行阶段时间：新增 executor_ip、executor_host、execute_start_time
-- 背景：周期调度（TASK）、规则调度（RULE）、手动执行（MANUAL）都要能回答
--       「在哪台机器上执行的、什么时间触发的、什么时间开始执行的、什么时间执行完成」。
-- 口径：trigger_time 触发时刻（TASK 为执行器收到调度中心触发请求的时间，xxl-job 2.3.0
--       执行器上下文拿不到调度中心触发时刻；RULE 为收到数据变更/启用存量评估的时间）；
--       execute_start_time 实际开始调用行为的时间，触发后的排队/规则求值耗时不计入执行；
--       complete_time 执行结束时间（成功或失败都写）。
--       executor_ip/executor_host 为执行进程所在机器，多执行器部署时用于定位执行节点。
-- 说明：三列均可空，存量历史行保持 NULL；查询走 /action/scheduling/result，
--       SchedulingResultVO 已带出这三个字段。该脚本可重复执行。
-- =============================================================================

SET search_path TO ontology;

ALTER TABLE ontology.action_handle_log ADD COLUMN IF NOT EXISTS executor_ip varchar(64);
ALTER TABLE ontology.action_handle_log ADD COLUMN IF NOT EXISTS executor_host varchar(255);
ALTER TABLE ontology.action_handle_log ADD COLUMN IF NOT EXISTS execute_start_time timestamp;

COMMENT ON COLUMN ontology.action_handle_log.executor_ip IS '执行机IP';
COMMENT ON COLUMN ontology.action_handle_log.executor_host IS '执行机主机名';
COMMENT ON COLUMN ontology.action_handle_log.execute_start_time IS '实际开始执行行为的时间（触发后可能排队/规则求值，与 trigger_time 区分）';