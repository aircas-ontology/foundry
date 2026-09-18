package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 表智能匹配总体结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "表智能匹配结果")
public class TableMatchResultVO {

    @Schema(name = "userInput", description = "原始用户输入，回显用", example = "构建一个卫星本体对象")
    private String userInput;

    @Schema(name = "datasourceId", description = "数据源id", example = "1")
    private Integer datasourceId;

    @Schema(name = "datasourceName", description = "数据源名称", example = "pg_main")
    private String datasourceName;

    @Schema(name = "reasoning", description = "LLM 选择主表的理由")
    private String reasoning;

    @Schema(name = "tables", description = "匹配到的表列表：第 1 个为主表，其余为外键关联表")
    private List<TableMatchVO> tables;
}
