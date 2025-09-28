package com.aircas.ptr.foundry.ontology.common.vo;


import com.aircas.ptr.foundry.common.constant.PostgresDataTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
public class PropertyVO {

    private String propertyName;

    private Object propertyValue;

    private Boolean isPrimaryKey;

    private PostgresDataTypeEnum datatype;
}
