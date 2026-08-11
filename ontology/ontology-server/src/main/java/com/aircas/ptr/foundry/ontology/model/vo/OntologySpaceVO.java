package com.aircas.ptr.foundry.ontology.model.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ApiModel(description = "本体空间VO")
public class OntologySpaceVO {

    @ApiModelProperty(name = "icon", value = "空间图标url")
    private String iconUrl;

    @ApiModelProperty(name = "displayName", value = "空间名称", example = "xxx战场")
    private String displayName;

    @ApiModelProperty(name = "apiName", value = "空间api名称", example = "space_a")
    private String apiName;

    @ApiModelProperty(name = "description", value = "空间描述", example = "这是空间描述")
    private String description;

    @ApiModelProperty(name = "spaceId", value = "空间id", example = "1")
    private Integer spaceId;

    /**
     * 本体数量统计
     */
    @ApiModelProperty(name = "ontologyCount", value = "本体数量", example = "10")
    private Integer ontologyCount;


    @ApiModelProperty(notes = "行为统计")
    private Integer actionCount;

    @ApiModelProperty(notes = "本体属性统计")
    private Integer propertyCount;


    /**
     * 本体关系统计
     */
    @ApiModelProperty(notes = "本体关系统计")
    private Integer linkCount;
}
