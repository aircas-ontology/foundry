package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "参数")
public class FunctionParameter {

    @Schema(name = "paramName", description = "参数名称")
    @NotBlank(message = "paramName is empty")
    private String paramName;

    @Schema(name = "paramType", description = "参数类型（LIST 类型时后端据此把 fields 组装为数组而非对象）")
    private FunctionParamTypeEnum paramType;

    @Schema(name = "paramValue", description = "参数值（标量参数直接取值；对象参数若逐字段填写可不传，由 fields 组装）")
    private Object paramValue;

    @Schema(name = "fields", description = "子字段列表（对象/复合参数按层级逐字段填值时使用，后端据此递归组装成嵌套对象）")
    private List<FunctionParameter> fields;
}
