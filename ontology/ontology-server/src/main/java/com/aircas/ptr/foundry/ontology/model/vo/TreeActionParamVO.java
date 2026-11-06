package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 行为树可绑定行为的参数定义，前端据此把算子入参与黑板键对上。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树可绑定行为的参数")
public class TreeActionParamVO {

    @Schema(name = "name", description = "参数名，行为的外部输入参数按它从黑板取值", example = "satelliteId")
    private String name;

    @Schema(name = "type", description = "参数类型", example = "STRING")
    private FunctionParamTypeEnum type;

    @Schema(name = "description", description = "参数说明")
    private String description;
}
