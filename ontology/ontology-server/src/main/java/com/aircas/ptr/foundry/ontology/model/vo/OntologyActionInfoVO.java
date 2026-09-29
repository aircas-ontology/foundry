package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "本体行为信息")
public class OntologyActionInfoVO {

    @Schema(name = "icon", description = "本体icon", example = "http://127.0.0.1/a.jpeg")
    private String icon;

    @Schema(name = "ontologyUniqIdentifier", description = "本体id", example = "123456")
    private String ontologyUniqIdentifier;

    @Schema(name = "actionApi", description = "api名称", example = "satellite")
    private String actionApi;

    @Schema(name = "description", description = "行为描述", example = "这是一个行为")
    private String description;

    @Schema(name = "displayName", description = "行为显示名称", example = "调用函数")
    private String displayName;

    @Schema(name = "functionApi", description = "本体下函数api", example = "getInfo")
    private String functionApi;

    @Schema(name = "functionDescription", description = "函数描述", example = "这是一个函数")
    private String functionDescription;
}
