package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "创建本体分组")
public class OntologyGroupCreateParam extends OntologySpaceIdParam {

    @Schema(name = "groupName", description = "分组名称", required = true, example = "远海远域")
    @NotBlank(message = "groupName is empty")
    private String groupName;

    @Schema(name = "description", description = "分组描述", required = true, example = "用于远海远域场景使用")
    @NotBlank(message = "description is empty")
    private String description;

    @Schema(name = "icon", description = "分组图标url", required = false)
    private String iconUrl;
}
