package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.GroupIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "更新本体分组")
public class OntologyGroupUpdatedParam extends OntologyGroupCreateParam {

    @GroupIdVerify
    @Schema(name = "groupId", description = "分组id", required = true)
    private String groupId;
}
