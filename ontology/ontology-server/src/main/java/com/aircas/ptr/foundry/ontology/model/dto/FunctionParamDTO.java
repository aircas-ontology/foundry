package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class FunctionParamDTO {
    //参数名
    private String paramName;
    //参数类型
    private String paramType;
    //参数结构 Object
    private String paramSchema;
    //分类   input[输入参数]  | output[输出返回值]
    private String category;
    //参数顺序
    private int paramOrder;
    //全限定类名
    private String referenceType;
}
