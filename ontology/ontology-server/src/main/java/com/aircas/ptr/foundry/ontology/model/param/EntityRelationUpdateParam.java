package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "实体关系更新请求")
public class EntityRelationUpdateParam {

    @ApiModelProperty(name = "linkUniqIdentifier" , value = "关系id", example = "edf3451")
    @NotBlank(message = "linkUniqIdentifier is empty")
    private String linkUniqIdentifier;

    @ApiModelProperty(name = "entityPrimaryKeyFrom", value = "开始实体主键值")
    @NotNull(message = "entityPrimaryKeyFrom is null")
    private Object entityPrimaryKeyFrom;

    @ApiModelProperty(name = "entityPrimaryKeyTo", value = "结束实体主键值")
    @NotNull(message = "entityPrimaryKeyFrom is null")
    private Object entityPrimaryKeyTo;


    @ApiModelProperty(name = "visibilityWindows", value = "可见窗口列表")
    private List<VisibilityWindow> visibilityWindows;


    @ApiModelProperty(name = "status", value = "关系状态")
    @NotNull(message = "status is null")
    private Status status;
}
