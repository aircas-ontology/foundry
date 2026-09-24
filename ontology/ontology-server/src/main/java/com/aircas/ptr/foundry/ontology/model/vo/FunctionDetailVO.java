package com.aircas.ptr.foundry.ontology.model.vo;


import com.aircas.ptr.foundry.ontology.model.param.BasicQueryConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "函数详细信息VO")
public class FunctionDetailVO extends FunctionInfoVO {


    @Schema(name = "params", description = "函数参数")
    private List<FunctionParameterVO> params;

    @Schema(name = "code", description = "函数代码：只有 CUSTOMIZE 类型会存在")
    private String code;

    @Schema(name = "referenceName", description = "函数全限定名称：只有 EXTERNAL 类型会存在")
    private String referenceName;

    @Schema(name = "queryConfig", description = "基础查询算子配置：只有 BASIC_QUERY 类型会存在")
    private BasicQueryConfig queryConfig;

}
