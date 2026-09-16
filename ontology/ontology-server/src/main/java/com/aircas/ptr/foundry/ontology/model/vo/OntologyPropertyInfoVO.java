package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyPropertyPrimaryCategoryEnum;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体属性基本信息")
public class OntologyPropertyInfoVO {


    @Schema(name = "displayName", description = "属性名称", example = "名称")
    private String displayName;

    @Schema(name = "description", description = "属性描述", example = "描述")
    private String description;

    /**
     * 是否为主键，1是0否
     */
    @Schema(name = "isPrimaryKey", description = "是否为主键")
    private Boolean isPrimaryKey;

    /**
     * 是否为名称键，将该属性作为本体的显示名称，1是0否
     */
    @Schema(name = "isTitleKey", description = "是否为名称键")
    private Boolean isTitleKey;

    /**
     * 唯一标示
     */
    @Schema(name = "uniqueIdentifier", description = "abcd")
    private String uniqueIdentifier;


    @Schema(name = "ontologyUniqueIdentifier", description = "123")
    private String ontologyUniqueIdentifier;

    /**
     * 属性标签
     */
    @Schema(name = "tag", description = "属性标签")
    private String tag;


    @Schema(name = "primaryCategory", description = "属性一级分类")
    private String primaryCategory;

    @Schema(name = "secondaryCategory", description = "属性二级分类")
    private String secondaryCategory;

    /**
     * 属性默认值
     */
    @Schema(name = "defaultValue", description = "属性默认值")
    private String defaultValue;

    /**
     * 属性存储分组
     */
    @Schema(name = "storageGroup", description = "属性存储分组", example = "123")
    private String storageGroup;

    /**
     * 属性分类id
     */
    @Schema(name = "categoryId", description = "属性分类id", example = "10")
    private Integer categoryId;

    @Schema(name = "metadata", description = "属性元数据")
    private JsonNode metadata;
}
