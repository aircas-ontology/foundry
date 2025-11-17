package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.common.constant.FunctionTypeEnum;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;


@Data
@Entity
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "function")
public class Function {


    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;


    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * Column: api
     */
    private String api;

    /**
     * Column: desc
     */
    private String description;

    /**
     * Column: displayName
     */
    private String displayName;

    /**
     * Column: status
     */
    private Integer status;

    /**
     * Column: id
     */
    @Id
    @Column(name = "id")
    private Long id;


    /**
     * 自定义函数类型才保存code
     */
    private String code;


    /**
     * 已存在函数类型才保存引入的函数全限定名称
     */
    private String referenceName;

    /**
     * FunctionTypeEnum
     */
    private FunctionTypeEnum type;
}