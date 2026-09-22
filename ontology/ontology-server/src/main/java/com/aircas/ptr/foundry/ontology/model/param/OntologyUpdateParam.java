package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "ontology update request param")
public class OntologyUpdateParam extends OntologyIdentifierParam {

    /**
     * 图标
     */
    @Schema(name = "iconUrl", description = "本体图标", example = "飞机图标base64")
    private String icon;

    /**
     * 本体名称
     */
    @Schema(name = "displayName", description = "本体名称", example = "飞机")
    @NotBlank(message = "displayName is empty")
    private String displayName;


    /**
     * 本体描述
     */
    @Schema(name = "description", description = "本体描述", example = "这是一架我方战斗机")
    private String description;

    /**
     * 本体描述
     */
    @Schema(name = "groupIds", description = "分组ids", example = "[123,456]")
    @NotEmpty(message = "groupIds is empty")
    private List<String> groupIds;


    @Schema(name = "categoryId", description = "分类id", example = "1")
    private Integer categoryId;

}

