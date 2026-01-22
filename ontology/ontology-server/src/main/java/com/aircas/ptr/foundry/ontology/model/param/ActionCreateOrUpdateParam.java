package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @author yangj
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "新增/编辑行为请求")
public class ActionCreateOrUpdateParam extends OntologyIdentifierParam {

    @ApiModelProperty(name = "icon", value = "图片url", example = "http://192.168.9.11/aa.jpeg", required = true)
    private String icon;

    @ApiModelProperty(value = "api名称", example = "satellite", required = true)
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @ApiModelProperty(value = "本体下函数api", example = "satellite")
    private String functionApi;

    @ApiModelProperty(value = "行为描述", example = "这是一个行为")
    private String description;

    @ApiModelProperty(value = "行为显示名称", example = "调用函数")
    @NotBlank(message = "displayName is empty")
    private String displayName;


    @ApiModelProperty(value = "行为关系映射")
    private ActionLinkMappingParam linkMapping;


    @ApiModelProperty(value = "行为参数列表")
    private List<ActionParamMappingCreateParam> mappingIns;
}
