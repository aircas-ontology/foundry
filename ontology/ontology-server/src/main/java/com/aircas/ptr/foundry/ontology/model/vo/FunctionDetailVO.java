package com.aircas.ptr.foundry.ontology.model.vo;


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
@Schema(description = "函数详细信息VO")
public class FunctionDetailVO extends FunctionInfoVO {


    @Schema(name = "params", description = "函数参数")
    private List<FunctionParameterVO> params;

    @Schema(name = "referenceName", description = "函数全限定名称")
    private String referenceName;

    @Schema(name = "code", description = "函数代码：只有自定义函数会存在")
    private String code;

}
