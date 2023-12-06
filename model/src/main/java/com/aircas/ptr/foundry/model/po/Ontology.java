package com.aircas.ptr.foundry.model.po;

import com.aircas.ptr.foundry.model.po.base.Bean;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 目录表
 */
@Data
@Entity
@Table(name = "ontology")
public class Ontology extends Bean {

    /**
     * 名称
     */
    @Column(name = "name")
    private String name;

    /**
     * 所属业务领域
     */
    @Column(name = "domain")
    private String domain;

    /**
     * 图标
     */
    @Column(name = "icon")
    private String icon;

    /**
     * 描述
     */
    @Column(name = "desc")
    private String desc;

    /**
     * 本体包含的字段
     */
    @Column(name = "fileds")
    private String fileds;

}
