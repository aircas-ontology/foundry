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
@Schema(description = "实体属性详情VO")
public class EntityPropertyDetailVO  {

    @Schema(name = "primaryCategory", description = "属性一级分类", example = "基本属性")
    private String primaryCategory;

    @Schema(name = "secondaryCategory", description = "属性二级分类", example = "基本属性")
    private String secondaryCategory;

    @Schema(name = "tag", description = "属性标签", example = "基本属性")
    private String tag;

    @Schema(name = "propertyUniqIdentifier", description = "属性名称", example = "名称")
    private String propertyUniqIdentifier;

    @Schema(name = "propertyApiName", description = "属性api名称", example = "名称")
    private String propertyApiName;

    @Schema(name = "propertyDisplayName", description = "属性名称", example = "名称")
    private String propertyDisplayName;

    @Schema(name = "propertyValues", description = "属性值列表", example = "[123,456]")
    private List<Object> propertyValues;

    @Schema(name = "primaryKey", description = "主键", example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "categoryId", description = "属性分类id", example = "10")
    private Integer categoryId;

    @Schema(name = "metadata", description = "属性元数据")
    private JsonNode metadata;

}
