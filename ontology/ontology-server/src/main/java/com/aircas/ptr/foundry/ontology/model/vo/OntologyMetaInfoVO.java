package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 16:10
 */

@Data
@Builder
@Accessors(chain = true)
@ApiModel(value = "本体元数据信息")
public class OntologyMetaInfoVO {

    /**
     * 唯一标识
     */
    @ApiModelProperty(name = "uniqueIdentifier" ,value = "本体id")
    private String uniqueIdentifier;


    /**
     * 记录创建时间
     */
    @ApiModelProperty(name = "createTime",value = "创建时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    /**
     * 记录修改时间
     */
    @ApiModelProperty(name = "updateTime",value = "修改时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;

    /**
     * 图标
     */
    @ApiModelProperty(name = "icon",value = "图标")
    private String icon;

    /**
     * 本体名称
     */
    @ApiModelProperty(name = "displayName", value = "本体名称", example = "飞机")
    private String displayName;


    /**
     * 本体描述
     */
    @ApiModelProperty(name = "description",value = "本体描述")
    private String description;

    /**
     * 在代码里用的本体名称
     */
    @ApiModelProperty(name = "apiName",value = "在代码里用的本体名称")
    private String apiName;

    /**
     * 分组
     */
    @ApiModelProperty(name = "metaGroupId",value = "分组")
    private List<String> metaGroupId;

}

