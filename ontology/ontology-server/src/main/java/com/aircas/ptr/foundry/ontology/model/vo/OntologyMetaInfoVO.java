package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.Set;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ApiModel(value = "本体元数据信息")
public class OntologyMetaInfoVO {

    /**
     * 唯一标识
     */
    @ApiModelProperty(name = "uniqueIdentifier", value = "本体id")
    private String uniqueIdentifier;


    /**
     * 记录创建时间
     */
    @ApiModelProperty(name = "createTime", value = "创建时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    /**
     * 记录修改时间
     */
    @ApiModelProperty(name = "updateTime", value = "修改时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;

    /**
     * 最新查看时间
     */
    @ApiModelProperty(name = "latestQueryTime", value = "最新查看时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date latestQueryTime;

    /**
     * 图标
     */
    @ApiModelProperty(name = "icon", value = "图标")
    private String icon;

    /**
     * 本体名称
     */
    @ApiModelProperty(name = "displayName", value = "本体名称", example = "飞机")
    private String displayName;


    /**
     * 本体描述
     */
    @ApiModelProperty(name = "description", value = "本体描述")
    private String description;

    /**
     * 在代码里用的本体名称
     */
    @ApiModelProperty(name = "apiName", value = "在代码里用的本体名称")
    private String apiName;

    /**
     * 分组
     */
    @ApiModelProperty(name = "metaGroupId", value = "分组")
    private Set<String> metaGroupId;

    @ApiModelProperty(name = "spaceId", value = "本体空间id")
    private Integer spaceId;

    @ApiModelProperty(name = "ontologyCategoryId", value = "本体分类id")
    private Integer ontologyCategoryId;

    @ApiModelProperty(name = "parentOntologyUniqueIdentifier", value = "父本体id")
    private String parentOntologyUniqueIdentifier;

    @ApiModelProperty(name = "parentOntologyDisplayName", value = "父本体名称")
    private String parentOntologyDisplayName;

    @ApiModelProperty(name = "entityCount", value = "实例数量")
    private Integer entityCount;

    @ApiModelProperty(name = "relationCount", value = "关系数量")
    private Integer relationCount;

    @ApiModelProperty(name = "propertyCount", value = "属性数量")
    private Integer propertyCount;

    @ApiModelProperty(name = "actionCount", value = "行为数量")
    private Integer actionCount;
}

