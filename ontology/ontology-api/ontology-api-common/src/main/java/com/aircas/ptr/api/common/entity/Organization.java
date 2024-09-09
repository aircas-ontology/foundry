package com.aircas.ptr.api.common.entity;

import lombok.Data;

import java.util.Date;

/**
 * 机构组织信息
 */
@Data
public class Organization {

    private Long id;

    private String organization;

    private int thread;

    private Date createTime;
}
