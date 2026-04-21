package com.aircas.ptr.foundry.ontology.model.vo;


import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScheduleStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(description = "行为调度VO")
public class ActionSchedulingInfoVO {

    @ApiModelProperty(name = "ontologyName", value = "本体名称", example = "名称")
    private String ontologyName;

    @ApiModelProperty(name = "ontologyIdentifier", value = "本体id", dataType = "java.lang.String", example = "abcdef", required = true)
    private String ontologyIdentifier;

    @ApiModelProperty(name = "actionApi", value = "行为id", dataType = "java.lang.String", example = "abcdef", required = true)
    private String actionApi;

    @ApiModelProperty(name = "schedulingName", value = "行为调度name", required = true, example = "name")
    private String schedulingName;

    @ApiModelProperty(name = "description", value = "行为调度描述", required = true, example = "description")
    private String description;

    @ApiModelProperty(name = "id", value = "行为调度id", required = true, example = "123")
    private Long id;

    @ApiModelProperty(name = "type", value = "行为调度类型：TASK（定时）、RULE（规则）", required = true, example = "123")
    private ActionSchedulingTypeEnum type;


    @ApiModelProperty(name = "status", value = "行为调度状态 START、STOP", required = true, example = "1")
    private ScheduleStatus status;

}
