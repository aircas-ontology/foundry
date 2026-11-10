package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "规则调度手动触发结果")
public class RuleTriggerResultVO {

    @Schema(name = "id", description = "规则调度id", example = "123")
    private Long id;

    @Schema(name = "name", description = "调度名称", example = "威胁响应规则")
    private String name;

    @Schema(name = "actionApi", description = "命中的行为api", example = "compute")
    private String actionApi;

    @Schema(name = "evaluatedInstances", description = "参与评估的实例数", example = "10")
    private Integer evaluatedInstances;

    @Schema(name = "matchedInstances", description = "命中规则的实例数", example = "2")
    private Integer matchedInstances;

    @Schema(name = "executed", description = "是否执行了行为", example = "true")
    private Boolean executed;

    @Schema(name = "msg", description = "执行消息")
    private String msg;

}
