package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 行为树可绑定行为的参数定义，前端据此展示行为要的入参和出参。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树可绑定行为的参数")
public class TreeActionParamVO {

    @Schema(name = "name", description = "参数名，行为的外部输入参数按它从执行请求取值（nodeExecutes.params 优先，回退 inputs）", example = "satelliteId")
    private String name;

    @Schema(name = "type", description = "参数类型", example = "STRING")
    private FunctionParamTypeEnum type;

    @Schema(name = "description", description = "参数说明")
    private String description;
}
