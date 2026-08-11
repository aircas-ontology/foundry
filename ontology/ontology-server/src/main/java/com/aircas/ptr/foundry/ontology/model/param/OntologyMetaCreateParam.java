package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.GroupIdsVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyApiNameVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyDisplayNameVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "ontology create request param")
public class OntologyMetaCreateParam extends OntologySpaceIdParam {

    @ApiModelProperty(name = "icon", value = "本体图标url", dataType = "java.lang.String", required = false)
    private String iconUrl;

    @ApiModelProperty(name = "displayName", value = "本体名称", dataType = "java.lang.String", example = "飞机", required = true)
    @NotBlank(message = "displayName is empty")
    //@OntologyDisplayNameVerify
    private String displayName;

    @ApiModelProperty(name = "description", value = "本体描述", dataType = "java.lang.String", example = "这是一架我方战斗机")
    private String description;

    @ApiModelProperty(name = "apiName", value = "在代码里用的本体名称", dataType = "java.lang.String", example = "airplane", required = true)
    @NotBlank(message = "apiName is empty")
    //@OntologyApiNameVerify
    private String apiName;

    @ApiModelProperty(name = "groupIds", value = "分组ids", example = "[123,456]", required = true)
    //@GroupIdsVerify
    private Set<String> groupIds;

    @ApiModelProperty(name = "parentOntologyUniqueIdentifier", value = "继承的本体id", example = "8039c5f9-5579-4ee4-ba94-b2f25f785dd6")
    private String parentOntologyUniqueIdentifier;


}
