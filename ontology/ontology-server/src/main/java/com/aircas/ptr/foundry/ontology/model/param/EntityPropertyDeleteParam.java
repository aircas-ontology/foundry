package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "实体属性删除请求")
public class EntityPropertyDeleteParam extends OntologyIdentifierParam {

    @ApiModelProperty(name = "entityPrimaryKey", value = "实体主键")
    @NotNull(message = "实体主键为空")
    private Object entityPrimaryKey;

    @ApiModelProperty(name = "storageGroup", value = "属性存储分组名称", example = "main")
    @NotBlank(message = "storageGroup is empty")
    private String storageGroup;

    @ApiModelProperty(name = "dataPrimaryKey", value = "属性数据主键")
    @NotNull(message = "dataPrimaryKey is empty")
    private Object dataPrimaryKey;


}
