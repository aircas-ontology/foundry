package com.aircas.ptr.foundry.topic.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("event_topic")
public class EventTopic {

    /**
     * 事件主题编号，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件名称
     */
    private String eventName;

    /**
     * 事件主题类别
     */
    private String category;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 地理区域
     */
    private String geoRegion;

    /**
     * 区域点
     */
    private String regionPoint;

    /**
     * 事件主题描述
     */
    private String description;

    /**
     * 关联本体空间，多个用逗号分割
     */
    private String ontologySpace;

    /**
     * 状态：0执行中，1执行成功，2执行失败
     */
    private Integer status;

    /**
     * 是否删除：0未删除，1已删除
     */
    private Integer deleted;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
