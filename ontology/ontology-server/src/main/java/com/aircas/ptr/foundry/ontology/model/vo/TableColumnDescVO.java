package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表描述信息VO")
public class TableColumnDescVO {

    @Schema(name = "columnName",description = "列名")
    private String columnName;

    @Schema(name = "description",description = "列描述信息")
    private String description;

    @Schema(name = "type",description = "列数据类型")
    private String type;

    @Schema(name = "isPrimaryKey",description = "是否为主键")
    private Boolean isPrimaryKey;
}
