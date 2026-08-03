package com.aircas.ptr.foundry.ontology.model.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(value = "实体属性信息VO")
public class EntityPropertyRowDetailVO {

    @ApiModelProperty(name = "primaryKey", value = "实体主键", example = "1")
    private Object entityPrimaryKey;

    @ApiModelProperty(name = "ontologyUniqueIdentifier", value = "本体id")
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(name = "propertyGroups", value = "属性分组")
    private List<PropertyGroup> propertyGroups;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @Accessors(chain = true)
    @ApiModel(value = "实体分组")
    public static class PropertyGroup {

        @ApiModelProperty(name = "storageGroup", value = "属性存储分组名称", example = "main")
        private String storageGroup;

        @ApiModelProperty(name = "groupData", value = "属性存储分组数据列表")
        private List<PropertyGroupData> groupDataList;

    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @Accessors(chain = true)
    @ApiModel(value = "属性存储分组数据")
    public static class PropertyGroupData {

        @ApiModelProperty(name = "dataPrimaryKey", value = "数据主键", example = "1")
        private Object dataPrimaryKey;

        @ApiModelProperty(name = "props", value = "属性信息")
        private List<PropertyInfo> props;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder
    @Accessors(chain = true)
    @ApiModel(value = "属性信息")
    public static class PropertyInfo {

        @ApiModelProperty(name = "primaryCategory", value = "属性一级分类", example = "设计制造")
        private String primaryCategory;

        @ApiModelProperty(name = "secondaryCategory", value = "属性二级分类", example = "基本属性")
        private String secondaryCategory;

        @ApiModelProperty(name = "tag", value = "属性标签", example = "基本属性")
        private String tag;

        @ApiModelProperty(name = "propertyUniqIdentifier", value = "属性唯一标识", example = "123")
        private String propertyUniqIdentifier;

        @ApiModelProperty(name = "propertyApiName", value = "属性api名称", example = "name")
        private String propertyApiName;

        @ApiModelProperty(name = "propertyDisplayName", value = "属性显示名称", example = "名称")
        private String propertyDisplayName;

        @ApiModelProperty(name = "propertyValues", value = "属性值", example = "1")
        private Object propertyValue;

        @ApiModelProperty(name = "categoryId", value = "属性分类id", example = "10")
        private Integer categoryId;


    }

}
