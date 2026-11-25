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
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@TableName("task_topic")
public class TaskTopic {

    /**
     * 任务编号，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 任务区域
     */
    private String taskRegion;

    /**
     * 区域点位，由页面传入
     */
    private String regionPoint;

    /**
     * 任务开始时间
     */
    private Date startTime;

    /**
     * 任务结束时间
     */
    private Date endTime;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 关联本体空间，多个用逗号分割
     */
    private String ontologySpace;

    /**
     * 任务状态：0执行中，1执行完成
     */
    private Integer status;

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
     * 目标数量。由后续统计接口写入，创建和编辑不传。
     */
    private Integer targetCount;

    /**
     * 海上数量
     */
    private Integer seaCount;

    /**
     * 空中数量
     */
    private Integer airCount;

    /**
     * 陆上数量
     */
    private Integer landCount;

    /**
     * 事件数量
     */
    private Integer eventCount;

    /**
     * 是否删除：0未删除，1已删除
     */
    private Integer deleted;
}
