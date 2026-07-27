package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "实体属性插入请求")
public class EntityPropertyInsertParam extends OntologyIdentifierParam {

    @ApiModelProperty(name = "entityPrimaryKey", value = "实体主键")
    @NotNull(message = "实体主键为空")
    private Object entityPrimaryKey;

    @ApiModelProperty(name = "entityProperties", value = "实体属性列表")
    @Size(min = 1, message = "实体属性列表为空")
    @Valid
    private List<EntityCreateParam.EntityProperty> entityProperties;


}
