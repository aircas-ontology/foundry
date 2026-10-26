package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 函数编排对外暴露的输入/输出参数定义，序列化后存入 function.code。
 * <p>落库时按 {@code name} 生成 function_param 记录，参数类型由 {@code type}
 * （string/number/boolean/object/array）映射为 FunctionParamTypeEnum。</p>
 */
@Data
@SuperBuilder

@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排对外参数定义")
public class CompositeParameter {

    @Schema(name = "id", description = "参数标识（编排画布内部使用，可用参数名代替）", example = "p_input_x")
    private String id;

    @Schema(name = "name", description = "参数名称，落库为 function_param.param_name", example = "startTime")
    private String name;

    @Schema(name = "type", description = "参数类型：string/number/boolean/object/array", example = "string")
    private String type;

    @Schema(name = "description", description = "参数描述")
    private String description;
}