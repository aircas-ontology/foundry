package com.aircas.ptr.foundry.ontology.entity.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@TableName(value = "entity_property_mapping")
public class EntityPropertyMappingPO extends BasePO {

    private String entityTable;

    private String entityPropertyTable;

    private String entityTableKey;

    private String entityPropertyTableKey;

}
