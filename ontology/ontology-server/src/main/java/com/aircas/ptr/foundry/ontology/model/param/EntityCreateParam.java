package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "实体创建请求")
public class EntityCreateParam extends OntologyIdentifierParam {


    @Schema(name = "entityList", description = "实体列表")
    @Size(min = 1,message = "实体列表为空")
    @Valid
    private List<Entity> entityList;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    @Schema(description = "实体对象")
    public static class Entity {

        @Schema(name = "entityProperties", description = "实体属性列表")
        @Size(min = 1,message = "实体属性列表为空")
        @Valid
        private List<EntityProperty>  entityProperties;

    }



    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    @Schema(description = "实体属性")
    public static class EntityProperty {


        @Schema(name = "storageGroup", description = "属性存储分组名称", example = "main")
        @NotBlank(message = "storageGroup is empty")
        private String storageGroup;

        @Schema(name = "props", description = "属性信息")
        @Size(min = 1,message = "分组属性列表为空")
        @Valid
        private List<List<PropertyInfo>> props;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @Accessors(chain = true)
    @Schema(description = "属性信息")
    public static class PropertyInfo {

        @Schema(name = "propertyApiName", description = "属性api名称", example = "name")
        @NotBlank(message = "propertyApiName is empty")
        private String propertyApiName;

        @Schema(name = "propertyValues", description = "属性值", example = "1")
        @NotNull(message = "propertyValue is null")
        private Object propertyValue;

    }

}
