package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "本体空间创建请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySpaceCreateParam {

    @Schema(name = "iconUrl", description = "空间图标url")
    private String iconUrl;

    @Schema(name = "displayName", description = "空间名称", example = "xxx战场", required = true)
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "description", description = "空间描述", example = "这是空间描述")
    private String description;

    @Schema(name = "apiName", description = "空间api名称", example = "space_a", required = true)
    @NotBlank(message = "apiName is empty")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "空间api名称格式不合法")
    private String apiName;
}
