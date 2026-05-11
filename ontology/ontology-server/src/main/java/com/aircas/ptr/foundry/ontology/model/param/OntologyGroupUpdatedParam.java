package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.GroupIdVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@ApiModel(description = "更新本体分组")
public class OntologyGroupUpdatedParam extends OntologyGroupCreateParam {

    @GroupIdVerify
    @ApiModelProperty(name = "groupId", value = "分组id", required = true)
    private String groupId;
}
