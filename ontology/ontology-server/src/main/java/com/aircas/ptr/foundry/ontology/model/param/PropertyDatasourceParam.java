package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "属性数据源请求")
public class PropertyDatasourceParam {

    @Schema(name = "schemaName", description = "schema名称", required = true, example = "public")
    private String schemaName;

    @Schema(name = "datasourceId", description = "数据源表名", required = true, example = "xtmb")
    private String datasourceId;

    @Schema(name = "datasourceColumnName", description = "数据源列名", required = true, example = "id")
    private String datasourceColumnName;


}
