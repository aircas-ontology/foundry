package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class FunctionParamDTO {
    //参数名
    private String paramName;
    //参数类型
    private FunctionParamTypeEnum paramType;
    //参数结构 Object
    private String paramSchema;
    //分类   input[输入参数]  | output[输出返回值]
    private FunctionParamCategoryEnum category;
    //参数顺序
    private Integer paramOrder;
    //全限定类名
    private String referenceType;

    private String description;

    /** 子字段列表（参数类型为自定义类/接口时展开，标量参数为 null） */
    private List<FunctionParamDTO> fields;
}
