package com.aircas.ptr.foundry.ontology.common.param;


import com.aircas.ptr.foundry.common.constant.CountTypeEnum;
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
public class EntityAssociateDatasourceParam {

    private String datasourceId;

    /**
     * 需要获取该表的数据数量
     */
    private CountTypeEnum count;
}
