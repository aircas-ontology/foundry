package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.persistence.Entity;
import java.util.Date;

@Data
@Entity
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "function_param")
public class FunctionParamPO {


    @TableId(type = IdType.AUTO)
    private Long id;

    private Long functionId;

    private String paramName;

    private FunctionParamTypeEnum paramType;

    private String paramSchema;

    private Integer paramOrder;

    private String description;

    private String typeReferenceName;

    private FunctionParamCategoryEnum category;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;


    /**
     * 记录更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
