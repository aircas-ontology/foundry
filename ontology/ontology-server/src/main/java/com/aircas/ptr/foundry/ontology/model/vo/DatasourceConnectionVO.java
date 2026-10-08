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
@Schema(description = "数据源连接VO（仅暴露核心字段，不含密码等敏感信息）")
public class DatasourceConnectionVO {

    @Schema(description = "数据源主键id")
    private Integer id;

    @Schema(description = "数据源名称")
    private String name;

    @Schema(description = "数据库类型（POSTGRESQL / MYSQL / ORACLE / SQLSERVER 等）")
    private String dbType;

    @Schema(description = "主机地址")
    private String host;

    @Schema(description = "端口号")
    private Integer port;

    @Schema(description = "数据库名")
    private String dbName;

    @Schema(description = "Schema名")
    private String schemaName;

    @Schema(description = "数据源描述")
    private String description;
}
