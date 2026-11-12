package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 行为节点的配置。
 * <p>节点只决定绑哪个行为、重试几次。行为需要的实例主键、分组表行主键、关系对端主键、
 * 外部输入参数全部由执行请求按节点传（见 TreeExecuteParam.nodeExecutes），节点上不配这些数据，
 * 也不再从黑板取。失败重试在单次执行内同步进行，重试会重复落库，只应对可重复执行的行为开启；
 * 超时无法安全中断已经开始的行为调用，不再支持，需要限制后续步骤请用限时节点。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树行为节点配置")
public class BehaviorActionConfig {

    @Schema(name = "actionApi", description = "绑定的行为 api", example = "act.satellite.track")
    private String actionApi;

    @Schema(name = "timeoutSeconds", description = "已不支持：单轮同步执行无法中断已经开始的行为，配正数会被拒绝；0 或空表示不限制；限制后续步骤请改用限时节点")
    private Integer timeoutSeconds;

    @Schema(name = "retryTimes", description = "失败重试次数，0 或空表示不重试；重试会重复落库，只应对可重复执行的行为开启")
    private Integer retryTimes;
}
