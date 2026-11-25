package com.aircas.ptr.foundry.topic.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("event_agg")
public class EventAgg {

    /**
     * 聚合事件编号，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件编号
     */
    private String eventNo;

    /**
     * 事件标题
     */
    private String eventTitle;

    /**
     * 事件发生时间
     */
    private Date eventTime;

    /**
     * 事件状态
     */
    private String status;

    /**
     * 进程描述，如“开始起12小时0分”
     */
    private String process;

    /**
     * 事件所属任务名称
     */
    private String taskName;

    /**
     * 事件位置
     */
    private String location;

    /**
     * 事件经度
     */
    private Double eventLon;

    /**
     * 事件纬度
     */
    private Double eventLat;

    /**
     * 情报来源类型
     */
    private String sourceType;

    /**
     * 情报来源平台
     */
    private String sourcePlatform;

    /**
     * 置信度
     */
    private String confidence;

    /**
     * 事件内容
     */
    private String eventContent;

    /**
     * 事件阶段编号，对应事件阶段 id
     */
    private Long stageId;
}
