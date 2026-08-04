package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.databind.JsonNode;
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
@ApiModel(value = "实体属性详情VO")
public class EntityPropertyDetailVO  {

    @ApiModelProperty(name = "primaryCategory", value = "属性一级分类", example = "基本属性")
    private String primaryCategory;

    @ApiModelProperty(name = "secondaryCategory", value = "属性二级分类", example = "基本属性")
    private String secondaryCategory;

    @ApiModelProperty(name = "tag", value = "属性标签", example = "基本属性")
    private String tag;

    @ApiModelProperty(name = "propertyUniqIdentifier", value = "属性名称", example = "名称")
    private String propertyUniqIdentifier;

    @ApiModelProperty(name = "propertyApiName", value = "属性api名称", example = "名称")
    private String propertyApiName;

    @ApiModelProperty(name = "propertyDisplayName", value = "属性名称", example = "名称")
    private String propertyDisplayName;

    @ApiModelProperty(name = "propertyValues", value = "属性值列表", example = "[123,456]")
    private List<Object> propertyValues;

    @ApiModelProperty(name = "primaryKey", value = "主键", example = "1")
    private Object entityPrimaryKey;

    @ApiModelProperty(name = "categoryId", value = "属性分类id", example = "10")
    private Integer categoryId;

    @ApiModelProperty(name = "metadata", value = "属性元数据")
    private JsonNode metadata;

}
