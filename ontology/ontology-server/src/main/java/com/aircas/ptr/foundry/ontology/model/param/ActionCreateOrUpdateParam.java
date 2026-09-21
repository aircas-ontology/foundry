package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * @author yangj
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "新增/编辑行为请求")
public class ActionCreateOrUpdateParam extends OntologyIdentifierParam {

    @Schema(name = "icon", description = "图片url", example = "http://192.168.9.11/aa.jpeg", required = true)
    private String icon;

    @Schema(description = "api名称", example = "satellite", required = true)
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @Schema(description = "本体下函数api", example = "satellite")
    private String functionApi;

    @Schema(description = "行为描述", example = "这是一个行为")
    private String description;

    @Schema(description = "行为显示名称", example = "调用函数")
    @NotBlank(message = "displayName is empty")
    private String displayName;


    @Schema(description = "行为关系映射")
    private ActionLinkMappingParam linkMapping;


    @Schema(description = "行为参数列表")
    private List<ActionParamMappingCreateParam> mappingIns;
}
