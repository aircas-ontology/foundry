package com.aircas.ptr.foundry.ontology.model.vo;


import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScheduleStatus;
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
@Schema(description = "行为调度VO")
public class ActionSchedulingInfoVO {

    @Schema(name = "ontologyName", description = "本体名称", example = "名称")
    private String ontologyName;

    @Schema(name = "ontologyIdentifier", description = "本体id", example = "abcdef", required = true)
    private String ontologyIdentifier;

    @Schema(name = "actionApi", description = "行为id", example = "abcdef", required = true)
    private String actionApi;

    @Schema(name = "actionName", description = "行为名称", example = "舰船轨迹预测", required = true)
    private String actionName;


    @Schema(name = "schedulingName", description = "行为调度name", required = true, example = "name")
    private String schedulingName;

    @Schema(name = "description", description = "行为调度描述", required = true, example = "description")
    private String description;

    @Schema(name = "id", description = "行为调度id", required = true, example = "123")
    private Long id;

    @Schema(name = "type", description = "行为调度类型：TASK（定时）、RULE（规则）", required = true, example = "123")
    private ActionSchedulingTypeEnum type;


    @Schema(name = "status", description = "行为调度状态 START、STOP", required = true, example = "1")
    private ScheduleStatus status;

}
