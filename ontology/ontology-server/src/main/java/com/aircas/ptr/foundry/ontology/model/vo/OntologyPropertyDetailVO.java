package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体属性详细信息")
public class OntologyPropertyDetailVO extends OntologyPropertyInfoVO {

    @Schema(name = "datasourceColumnName", description = "数据源列名", example = "id")
    private String datasourceColumnName;

    @Schema(name = "datasourceId", description = "数据源表名", example = "xtmb")
    private String datasourceId;

    @Schema(name = "datasourceDescription", description = "数据源表名描述", example = "xtmb")
    private String datasourceDescription;

}
