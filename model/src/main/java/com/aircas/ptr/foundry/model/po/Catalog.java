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
@Table(name = "catalog")
public class Catalog extends Bean {

    /**
     * 业务节点code
     */
    @Column(name = "node_code")
    private String nodeCode;

    /**
     * 目录名称，即对应的元数据字段枚举项名称
     */
    @Column(name = "name")
    private String name;

    /**
     * 目录层级，from 1
     */
    @Column(name = "level")
    private Integer level;

    /**
     * 对应的元数据字段code
     */
    @Column(name = "field_code")
    private String fieldCode;

    /**
     * 对应的元数据字段枚举项code
     */
    @Column(name = "field_enum_item_code")
    private String fieldEnumItemCode;

    /**
     * 父目录id
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * 同层顺序，from 1
     */
    //@Column(name = "seq")
    //private Integer seq;
}
