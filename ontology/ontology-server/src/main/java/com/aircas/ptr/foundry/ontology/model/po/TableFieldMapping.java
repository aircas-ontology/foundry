package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("table_field_mapping")
public class TableFieldMapping {

    @TableId(type = IdType.AUTO)
    private Long id;


    private String sourceTableName;

    private String sourceColumnName;

    private String targetTableName;

    private String targetColumnName;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;


}
