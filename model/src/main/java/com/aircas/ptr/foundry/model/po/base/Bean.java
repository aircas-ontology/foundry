package com.aircas.ptr.foundry.model.po.base;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Id;
import java.util.Date;

@Data
public class Bean {

    /**
     * 由于涉及到跨节点同步，id通过snowflake生成
     */
    @Id
    @Column(name = "id")
    private Long id;

    /**
     * 状态
     *
     * @see Status
     */
    @Column(name = "status")
    private Integer status;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "modify_time")
    private Date modifyTime;
}
