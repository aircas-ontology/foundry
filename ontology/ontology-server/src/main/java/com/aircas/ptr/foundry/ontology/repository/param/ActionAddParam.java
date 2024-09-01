package com.aircas.ptr.foundry.ontology.repository.param;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * @author yangj
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "行为新增模型")
public class ActionAddParam {

    @ApiModelProperty(value = "id", example = "1264894134988943")
    private Long id;

    @ApiModelProperty(value = "行为api，用来调用", example = "cal")
    private String api;

    @ApiModelProperty(value = "函数api", example = "satellite")
    private String functionApi;

    @ApiModelProperty(value = "本体id", example = "fdsa-dfsaf-gdsagfd")
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(value = "行为描述", example = "这是一个行为")
    private String description;

    @ApiModelProperty(value = "行为显示名称", example = "调用函数")
    private String displayName;

    @ApiModelProperty(value = "任务开始时间，在functionApi不为空时生效", example = "2024-01-01 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date taskStartTime;

    @ApiModelProperty(value = "任务结束时间，在functionApi不为空时生效", example = "2024-01-02 00:00:00")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date taskEndTime;

    @ApiModelProperty(value = "任务开始时间，在functionApi不为空时生效", example = "30 * * 1/1 * ? *")
    private String taskCorn;

    @ApiModelProperty(value = "临时，任务的实体主键列表，以逗号分隔", example = "[\"7\",\"10\"]")
    private List<String> objectPrimaryKeys;


    @ApiModelProperty(value = "行为参数列表", example = "[{\"parameterName\":\"mbbh\",\"propertyUniqueIdentifier\":\"545649a4-8bba-4d0c-b265-362e85fd4fe1\"}]")
    private List<ActionMappingInAddParam> mappingIns;
}
