package com.aircas.ptr.foundry.sync.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.util.Date;

@Data
public class OntologyActionRule {
    /**
     * 主键自增
     */
    @TableId(type = IdType.ASSIGN_ID)
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
    private String fields;
    /**
     * 表达式内容
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
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    /**
     * 历史值:多个值用逗号分隔
     */
    private String historyValue;

}