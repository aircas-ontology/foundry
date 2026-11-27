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
@TableName("implicit_relation")
public class ImplicitRelation {

    /**
     * 编号，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 起始节点编号
     */
    private String startNodeCode;

    /**
     * 起始节点名称
     */
    private String startNodeName;

    /**
     * 所属空间
     */
    private String belongSpace;

    /**
     * 关联类型，由页面传入
     */
    private String relationType;

    /**
     * 关系跳数，由页面传入
     */
    private Integer hopCount;

    /**
     * 隐含关系数
     */
    private Integer implicitCount;

    /**
     * 进度状态：0未发现，1待校验，2已完成
     */
    private Integer progressStatus;

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
     * 更新人
     */
    private String updater;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
