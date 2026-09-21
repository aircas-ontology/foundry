package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "数据源VO")
public class DatasourceTableVO {

    @Schema(description = "schemaName", example = "public")
    private String schemaName;

    @Schema(description = "数据源标识", example = "xtmb")
    private String tableName;

    @Schema(description = "数据源描述", example = "系统目标")
    private String description;
}
