package com.aircas.ptr.foundry.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;


@Data
@Entity
@TableName(value = "function")
public class Function {


    /**
     * 记录创建时间
     */
    private Date createTime;


    /**
     * 记录创建时间
     */
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
     * Column: ObjectTypes
     */
    private String objectTypes;

}