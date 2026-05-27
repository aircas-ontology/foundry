package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyPropertyPrimaryCategoryEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ApiModel(description = "本体属性基本信息")
public class OntologyPropertyInfoVO {


    @ApiModelProperty(name = "displayName", value = "属性名称", example = "名称")
    private String displayName;

    @ApiModelProperty(name = "description", value = "属性描述", example = "描述")
    private String description;

    /**
     * 是否为主键，1是0否
     */
    @ApiModelProperty(name = "isPrimaryKey", value = "是否为主键")
    private Boolean isPrimaryKey;

    /**
     * 是否为名称键，将该属性作为本体的显示名称，1是0否
     */
    @ApiModelProperty(name = "isTitleKey", value = "是否为名称键")
    private Boolean isTitleKey;

    /**
     * 唯一标示
     */
    @ApiModelProperty(name = "uniqueIdentifier", value = "abcd")
    private String uniqueIdentifier;


    @ApiModelProperty(name = "ontologyUniqueIdentifier", value = "123")
    private String ontologyUniqueIdentifier;

    /**
     * 属性标签
     */
    @ApiModelProperty(name = "tag", value = "属性标签")
    private String tag;


    @ApiModelProperty(name = "primaryCategory", value = "属性一级分类")
    private String primaryCategory;

    @ApiModelProperty(name = "secondaryCategory", value = "属性二级分类")
    private String secondaryCategory;

    /**
     * 属性默认值
     */
    @ApiModelProperty(name = "defaultValue", value = "属性默认值")
    private String defaultValue;

    /**
     * 属性存储分组
     */
    @ApiModelProperty(name = "storageGroup", value = "属性存储分组", example = "123")
    private String storageGroup;
}
