package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "批量行为测试里一组组合的执行结果")
public class ActionTestCaseResultVO {

    @Schema(name = "seq", description = "组合序号，从 1 开始，按取值轴展开的顺序（越靠后的轴变化越快）", example = "1")
    private Integer seq;

    @Schema(name = "combo", description = "这组组合选中的取值：轴名 -> 值。"
            + "轴名形如 from、to、entityPrimaryKeys[对象id]、inputParams[入参名]；"
            + "inputParams 的取值是一组完整数据位置（实例主键 + 分组行主键）",
            example = "{\"from\":1,\"to\":10,\"inputParams[max_mach]\":{\"entityPrimaryKey\":101,\"dataPrimaryKeys\":{\"duty_log\":11}}}")
    private Map<String, Object> combo;

    @Schema(name = "result", description = "这组组合的算子返回结果（JSON）；执行失败时为空")
    private String result;

    @Schema(name = "error", description = "这组组合的失败原因；成功时为空")
    private String error;
}
