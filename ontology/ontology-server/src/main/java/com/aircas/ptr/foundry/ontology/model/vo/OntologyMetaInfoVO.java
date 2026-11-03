package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "对象元数据信息")
public class OntologyMetaInfoVO {

    /**
     * 唯一标识
     */
    @Schema(name = "uniqueIdentifier", description = "对象id")
    private String uniqueIdentifier;


    /**
     * 记录创建时间
     */
    @Schema(name = "createTime", description = "创建时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    /**
     * 记录修改时间
     */
    @Schema(name = "updateTime", description = "修改时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;

    /**
     * 最新查看时间
     */
    @Schema(name = "latestQueryTime", description = "最新查看时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date latestQueryTime;

    /**
     * 图标
     */
    @Schema(name = "icon", description = "图标")
    private String icon;

    /**
     * 对象名称
     */
    @Schema(name = "displayName", description = "对象名称", example = "飞机")
    private String displayName;


    /**
     * 对象描述
     */
    @Schema(name = "description", description = "对象描述")
    private String description;

    /**
     * 在代码里用的对象名称
     */
    @Schema(name = "apiName", description = "在代码里用的对象名称")
    private String apiName;

    /**
     * 分组
     */
    @Schema(name = "metaGroupId", description = "分组")
    private Set<String> metaGroupId;

    @Schema(name = "spaceId", description = "对象空间id")
    private Integer spaceId;

    @Schema(name = "ontologyCategoryId", description = "对象分类id")
    private Integer ontologyCategoryId;

    @Schema(name = "parentOntologyUniqueIdentifier", description = "父对象id")
    private String parentOntologyUniqueIdentifier;

    @Schema(name = "parentOntologyDisplayName", description = "父对象名称")
    private String parentOntologyDisplayName;

    @Schema(name = "entityCount", description = "实例数量")
    private Integer entityCount;

    @Schema(name = "relationCount", description = "关系数量")
    private Integer relationCount;

    @Schema(name = "propertyCount", description = "属性数量")
    private Integer propertyCount;

    @Schema(name = "actionCount", description = "行为数量")
    private Integer actionCount;
}

