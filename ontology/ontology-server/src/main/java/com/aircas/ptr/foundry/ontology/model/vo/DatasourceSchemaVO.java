package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 外部数据源 schema 清单项（按厂商分派：MySQL/MariaDB 走 {@code getCatalogs()} 返回数据库名，
 * 其余厂商走 {@code getSchemas()} 返回 schema 名）。
 *
 * <p>{@code schemaName} 是前端回传给 {@code /table/list}、{@code /table/columns}、
 * {@code /build_object} 的 {@code schemaName} 参数值；{@code catalogName} 是 JDBC 层面的归属容器名
 * （PG 上通常等于连接所在的数据库名，MySQL 上与 {@code schemaName} 相同）。</p>
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部数据源 schema 清单项")
public class DatasourceSchemaVO {

    @Schema(description = "schema 名（回传给 /table/list、/table/columns、build_object 的 schemaName 参数）")
    private String schemaName;

    @Schema(description = "JDBC catalog 名（MySQL/MariaDB 上等于 schemaName；PG/Oracle/SQLServer 上通常为连接所在的数据库名，可能为空）")
    private String catalogName;
}
