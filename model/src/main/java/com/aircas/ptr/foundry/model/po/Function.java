package com.aircas.ptr.foundry.model.po;

import lombok.Data;

import java.util.Date;


@Data
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
    private Long id;

    /**
     * Column: ObjectTypes
     */
    private String objectTypes;

}