package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "实体属性更新请求")
public class EntityPropertyUpdateParam extends OntologyIdentifierParam {

    @ApiModelProperty(name = "entityPrimaryKey", value = "实体主键")
    @NotNull(message = "实体主键为空")
    private Object entityPrimaryKey;

    @ApiModelProperty(name = "storageGroup", value = "属性存储分组名称", example = "main")
    @NotBlank(message = "storageGroup is empty")
    private String storageGroup;

    @ApiModelProperty(name = "dataPrimaryKey", value = "属性数据主键")
    @NotNull(message = "dataPrimaryKey is empty")
    private Object dataPrimaryKey;

    @ApiModelProperty(name = "props", value = "属性信息")
    @Size(min = 1, message = "分组属性列表为空")
    @Valid
    private List<EntityCreateParam.PropertyInfo> props;

}
