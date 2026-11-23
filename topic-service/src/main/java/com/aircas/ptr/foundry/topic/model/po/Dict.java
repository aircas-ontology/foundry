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
@TableName("dict")
public class Dict {

    /**
     * 字典编号
     */
    @TableId(type = IdType.INPUT)
    private String dictCode;

    /**
     * 字典名称
     */
    private String dictName;

    /**
     * 字典组编号
     */
    private String groupCode;

    /**
     * 字典组名称
     */
    private String groupName;

    /**
     * 字典状态：0正常，1删除
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
}
