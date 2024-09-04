package com.aircas.ptr.foundry.common.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@ApiModel
public class OntologyActionRuleDTO {

//    @ApiModelProperty(value = "主键自增")
//    private Long id;

    @ApiModelProperty(value = "本体行为id")
    private Integer ontologyActionId;

    @ApiModelProperty(value = "数据库模式")
    private String schema;

    @ApiModelProperty(value = "数据表")
    private String table;

    @ApiModelProperty(value = "数据库字段列表")
    private List<String> fields;

    @ApiModelProperty(value = "表达式内容")
    private String content;

    @ApiModelProperty(value = "执行器:Eval")
    private String executor;

    @ApiModelProperty(value = "规则名称")
    private String name;

//    @ApiModelProperty(value = "创建时间")
//    private Date createTime;
//
//    @ApiModelProperty(value = "更新时间")
//    private Date updateTime;

    @ApiModelProperty(value = "历史值")
    private String historyValue;

}