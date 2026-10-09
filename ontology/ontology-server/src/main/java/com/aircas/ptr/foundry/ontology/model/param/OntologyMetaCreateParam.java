package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.GroupIdsVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyApiNameVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyDisplayNameVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "ontology create request param")
public class OntologyMetaCreateParam extends OntologySpaceIdParam {

    @Schema(name = "iconUrl", description = "本体图标url", required = false)
    private String iconUrl;

    @Schema(name = "displayName", description = "本体名称", example = "飞机", required = true)
    @NotBlank(message = "displayName is empty")
    //@OntologyDisplayNameVerify
    private String displayName;

    @Schema(name = "description", description = "本体描述", example = "这是一架我方战斗机")
    private String description;

    @Schema(name = "apiName", description = "在代码里用的本体名称", example = "airplane", required = true)
    @NotBlank(message = "apiName is empty")
    //@OntologyApiNameVerify
    private String apiName;

    @Schema(name = "groupIds", description = "分组ids", example = "[123,456]", required = true)
    //@GroupIdsVerify
    private Set<String> groupIds;

    @Schema(name = "parentOntologyUniqueIdentifier", description = "继承的本体id", example = "8039c5f9-5579-4ee4-ba94-b2f25f785dd6")
    private String parentOntologyUniqueIdentifier;

    @Schema(name = "categoryId", description = "分类id", example = "1")
    private Integer categoryId;


}
