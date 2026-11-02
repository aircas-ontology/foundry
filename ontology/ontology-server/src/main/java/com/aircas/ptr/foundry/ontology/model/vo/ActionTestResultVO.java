package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为测试结果")
public class ActionTestResultVO {

    @Schema(name = "actionApi", description = "行为api", example = "syncSpeed")
    private String actionApi;

    @Schema(name = "message", description = "本次落库动作说明", example = "已向存储分组[extra]新增 1 行属性数据")
    private String message;

    @Schema(name = "result", description = "算子返回结果（JSON）；不绑算子的行为为空")
    private String result;
}
