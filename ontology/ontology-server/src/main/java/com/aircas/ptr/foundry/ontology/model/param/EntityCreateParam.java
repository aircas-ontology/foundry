package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
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
@ApiModel(description = "实体创建请求")
public class EntityCreateParam extends OntologyIdentifierParam {


    @ApiModelProperty(name = "entityList", value = "实体列表")
    @Size(min = 1,message = "实体列表为空")
    @Valid
    private List<Entity> entityList;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    @ApiModel(description = "实体对象")
    public static class Entity {

        @ApiModelProperty(name = "entityProperties", value = "实体属性列表")
        @Size(min = 1,message = "实体属性列表为空")
        @Valid
        private List<EntityProperty>  entityProperties;

    }



    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    @ApiModel(description = "实体属性")
    public static class EntityProperty {


        @ApiModelProperty(name = "storageGroup", value = "属性存储分组名称", example = "main")
        @NotBlank(message = "storageGroup is empty")
        private String storageGroup;

        @ApiModelProperty(name = "props", value = "属性信息")
        @Size(min = 1,message = "分组属性列表为空")
        @Valid
        private List<List<PropertyInfo>> props;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @Accessors(chain = true)
    @ApiModel(value = "属性信息")
    public static class PropertyInfo {

        @ApiModelProperty(name = "propertyApiName", value = "属性api名称", example = "name")
        @NotBlank(message = "propertyApiName is empty")
        private String propertyApiName;

        @ApiModelProperty(name = "propertyValues", value = "属性值", example = "1")
        @NotNull(message = "propertyValue is null")
        private Object propertyValue;

    }

}
