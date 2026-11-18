package com.aircas.ptr.foundry.ontology.service;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 手动触发行为执行时的日志记录。
 */
public interface ManualExecuteLogService {

    /**
     * 执行一次并把这条记录写进 action_handle_log：类型 MANUAL，action_handle_id 存行为 id，
     * 与定时、规则调度的日志同表同查询接口（{@code GET /action/scheduling/result?id=行为id&type=MANUAL}）。
     * <p>一次对外请求记一条：先落 PENDING，执行返回后回写说明与结果；抛异常记 FAILED 后原样上抛。
     * 只要没抛异常就算执行完成，树节点或某个组合的失败原因写在说明里，状态口径与定时、规则的日志保持一致。</p>
     *
     * @param ontologySpaceId 行为所属空间，行为 api 只在空间内唯一
     * @param actionApi       行为或行为树 api；定位不到行为时不记日志，由执行体按原有口径报无效 api
     * @param requestParam    本次请求入参，整份存进 request_param
     * @param execution       真正的执行体
     * @param messageOf       从执行结果里取一句说明，存进 msg
     */
    <T> T runAndLog(Integer ontologySpaceId, String actionApi, Object requestParam,
                    Supplier<T> execution, Function<T, String> messageOf);
}
