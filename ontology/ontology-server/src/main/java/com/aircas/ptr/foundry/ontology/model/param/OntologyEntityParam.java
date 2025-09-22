package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "实体属性更新请求")
public class OntologyEntityParam extends OntologyIdentifierParam{


    @ApiModelProperty(name = "entityPrimaryKey",value = "实体编号", example = "xxxx")
    @NotBlank(message = "entityPrimaryKey is empty")
    private String entityPrimaryKey;


    @ApiModelProperty(name = "properties", value = "更新的属性键值")
    @NotNull(message = "properties is null")
    private List<EntityPropertyParam> properties;

}
