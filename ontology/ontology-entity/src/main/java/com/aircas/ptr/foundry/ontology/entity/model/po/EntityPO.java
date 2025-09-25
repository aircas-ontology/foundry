package com.aircas.ptr.foundry.ontology.entity.model.po;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "document")
public class EntityPO extends BasePO{

    private String datasourceColumnName;

    private String datasourceId;

    private String tableName;
}
