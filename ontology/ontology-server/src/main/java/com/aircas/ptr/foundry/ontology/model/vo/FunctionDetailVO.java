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
@Schema(description = "函数版本详细信息")
public class FunctionDetailVO extends FunctionInfoVO {
    @Schema(description = "函数参数")
    private List<FunctionParameterVO> params;
    @Schema(description = "函数全限定名")
    private String referenceName;
    @Schema(description = "自定义函数代码")
    private String code;
}
