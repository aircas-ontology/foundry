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

    @Schema(name = "results", description = "每个选中的对象一条结果，顺序与行为里选对象的顺序一致；"
            + "聚合时逐个对象各算各的聚合值，查询时只有行为所绑的那一个对象")
    private List<ActionBasicFunctionObjectResultVO> results;
}
