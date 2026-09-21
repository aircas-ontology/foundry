package com.aircas.ptr.foundry.ontology.model.vo;


import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "实体属性信息VO")
public class EntityPropertyRowDetailVO {

    @Schema(name = "primaryKey", description = "实体主键", example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "ontologyUniqueIdentifier", description = "本体id")
    private String ontologyUniqueIdentifier;

    @Schema(name = "propertyGroups", description = "属性分组")
    private List<PropertyGroup> propertyGroups;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @Accessors(chain = true)
    @Schema(description = "实体分组")
    public static class PropertyGroup {

        @Schema(name = "storageGroup", description = "属性存储分组名称", example = "main")
        private String storageGroup;

        @Schema(name = "groupData", description = "属性存储分组数据列表")
        private List<PropertyGroupData> groupDataList;

    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @Accessors(chain = true)
    @Schema(description = "属性存储分组数据")
    public static class PropertyGroupData {

        @Schema(name = "dataPrimaryKey", description = "数据主键", example = "1")
        private Object dataPrimaryKey;

        @Schema(name = "props", description = "属性信息")
        private List<PropertyInfo> props;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @Accessors(chain = true)
    @Schema(description = "属性信息")
    public static class PropertyInfo {

        @Schema(name = "propertyUniqIdentifier", description = "属性唯一标识", example = "123")
        private String propertyUniqIdentifier;

        @Schema(name = "propertyApiName", description = "属性api名称", example = "name")
        private String propertyApiName;

        @Schema(name = "propertyDisplayName", description = "属性显示名称", example = "名称")
        private String propertyDisplayName;

        @Schema(name = "propertyValues", description = "属性值", example = "1")
        private Object propertyValue;

        @Schema(name = "categoryId", description = "属性分类id", example = "10")
        private Integer categoryId;

        @Schema(name = "metadata", description = "属性元数据")
        private JsonNode metadata;


    }

}
