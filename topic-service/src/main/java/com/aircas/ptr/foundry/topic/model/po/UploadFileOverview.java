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
@TableName("upload_file_overview")
public class UploadFileOverview {

    /**
     * 文件总览统计编号，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件编号
     */
    private Long fileId;

    /**
     * 文件总数
     */
    private Integer fileTotal;

    /**
     * 抽取列表，如[{"name":"实体","quantity":1,"proportion":0.25}]
     */
    private String extractList;

    /**
     * 关联目标数量
     */
    private Integer targetCount;

    /**
     * 隐含关系
     */
    private Integer implicitRelation;

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
