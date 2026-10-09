package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "本体空间修改请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySpaceUpdateParam {

    @Schema(name = "iconUrl", description = "空间图标url")
    private String iconUrl;

    @Schema(name = "displayName", description = "空间名称", example = "xxx战场", required = true)
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "description", description = "空间描述", example = "这是空间描述")
    private String description;

    @Schema(name = "spaceId", description = "空间id", example = "1", required = true)
    @NotNull(message = "spaceId is null")
    private Integer spaceId;
}
