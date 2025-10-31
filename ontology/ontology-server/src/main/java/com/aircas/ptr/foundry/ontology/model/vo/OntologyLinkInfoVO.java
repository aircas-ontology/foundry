package com.aircas.ptr.foundry.ontology.model.vo;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;


@Data
@Builder
@Accessors(chain = true)
@ApiModel(value = "本体关系数据信息")
public class OntologyLinkInfoVO {


    /**
     * link的unique identifier
     */
    @ApiModelProperty(name = "uniqueIdentifier", value = "link 唯一标识")
    private String uniqueIdentifier;

    /**
     * link的名称
     */
    @ApiModelProperty(name = "name", value = "link名称")
    private String name;

    /**
     * 记录创建时间
     */
    @ApiModelProperty(name = "createTime", value = "记录创建时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT)
    private Date createTime;

    /**
     * 记录修改时间
     */
    @ApiModelProperty(name = "updateTime", value = "记录修改时间")
    @JSONField(format = DATE_FORMAT_DEFAULT)
    private Date updateTime;

    /**
     * 开始本体unique identifier
     */
    @ApiModelProperty(name = "ontologyUniqueIdentifierFrom", value = "开始本体unique identifier")
    private String ontologyUniqueIdentifierFrom;

    /**
     * 开始本体名称
     */
    @ApiModelProperty(name = "ontologyNameFrom", value = "开始本体名称")
    private String ontologyNameFrom;

    /**
     * 开始本体icon
     */
    @ApiModelProperty(name = "ontologyIconFrom", value = "开始本体icon")
    private String ontologyIconFrom;

    /**
     * 结束本体id
     */
    @ApiModelProperty(name = "ontologyUniqueIdentifierTo", value = "结束本体id")
    private String ontologyUniqueIdentifierTo;

    /**
     * 结束本体名称
     */
    @ApiModelProperty(name = "ontologyNameTo", value = "结束本体名称")
    private String ontologyNameTo;

    /**
     * 开始本体icon
     */
    @ApiModelProperty(name = "ontologyIconTO", value = "开始本体icon")
    private String ontologyIconTO;

}


