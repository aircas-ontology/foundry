package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.BasicFunctionModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "基础函数行为执行结果")
public class ActionBasicFunctionResultVO {

    @Schema(name = "mode", description = "本次执行的操作方式：AGGREGATE 聚合 / QUERY 查询", example = "AGGREGATE")
    private BasicFunctionModeEnum mode;

    @Schema(name = "results", description = "本次查询的结果，一条：查询时是查中行合批后的整批结果；"
            + "聚合时是所选对象（可以多个）命中的值合到一批后算出的那一个统一聚合值，对象名见该条的 ontologyDisplayName")
    private List<ActionBasicFunctionObjectResultVO> results;

    @Schema(name = "writeBack", description = "本次执行的结果回写情况；行为没有配置回写时为空")
    private ActionBasicFunctionWriteBackVO writeBack;
}
