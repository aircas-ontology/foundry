package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 外部数据源表清单项（向导「选择表」步骤用）。
 * <p>与 {@link TableCommentVO} 分开维护：后者是 MCP 扫描工具的返回体，且在实现里按位置构造，
 * 加字段会静默错位；本 VO 多一个「字段数」，是向导页勾选表时的必要信息。
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部数据源表清单项")
public class DatasourceTableSummaryVO {

    @Schema(name = "tableName", description = "表名")
    private String tableName;

    @Schema(name = "tableComment", description = "表注释，厂商驱动未开启 remarks 时为空")
    private String tableComment;

    @Schema(name = "columnCount", description = "字段数")
    private Integer columnCount;
}
