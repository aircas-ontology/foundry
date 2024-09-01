package com.aircas.ptr.foundry.common.dto;

import lombok.Data;

import java.util.Date;

@Data
public class OntologyActionRuleDTO {
    /**
     * 主键自增
     */
    private Long id;

    /**
     * 本体行为id
     */
    private Integer ontologyActionId;

    /**
     * 数据库模式
     */
    private String schema;

    /**
     * 数据表
     */
    private String table;

    /**
     * 字段:多个值用逗号分隔
     */
    private String field;
    /**
     * 脚本内容
     */
    private String content;
    /**
     * 执行器:groovy
     */
    private String executor;
    /**
     * 规则名称
     */
    private String name;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 历史值:多个值用逗号分隔
     */
    private String historyValue;

}