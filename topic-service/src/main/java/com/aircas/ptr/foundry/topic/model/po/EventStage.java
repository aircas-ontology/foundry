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
@TableName("event_stage")
public class EventStage {

    /**
     * 事件阶段编号，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务编号
     */
    private Long taskCode;

    /**
     * 事件阶段名称
     */
    private String stageName;

    /**
     * 事件阶段开始时间
     */
    private Date startTime;

    /**
     * 事件阶段结束时间
     */
    private Date endTime;

    /**
     * 事件数量
     */
    private Integer eventCount;

    /**
     * 事件编号，多个用逗号分割
     */
    private String eventCodes;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 是否删除：0未删除，1已删除
     */
    private Integer deleted;
}
