package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 表字段匹配结果 VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "表字段匹配结果")
public class ColumnMatchVO {

    @Schema(name = "columnName", description = "字段名", example = "satellite_id")
    private String columnName;

    @Schema(name = "dataType", description = "字段数据类型", example = "bigint")
    private String dataType;

    @Schema(name = "description", description = "字段中文描述（来自数据库注释）", example = "卫星主键")
    private String description;

    @Schema(name = "primaryKey", description = "是否为主键")
    private Boolean primaryKey;

    @Schema(name = "foreignKey", description = "是否为外键")
    private Boolean foreignKey;

    @Schema(name = "refTable", description = "外键引用的表名，非外键时为 null", example = "orbit")
    private String refTable;

    @Schema(name = "refColumn", description = "外键引用的字段名，非外键时为 null", example = "id")
    private String refColumn;
}
