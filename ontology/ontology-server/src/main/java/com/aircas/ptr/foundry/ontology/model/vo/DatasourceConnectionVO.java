package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * 数据源连接配置 VO（对外返回，不含密码等敏感字段）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "数据源连接配置VO")
public class DatasourceConnectionVO {

    @Schema(name = "id", description = "主键id", example = "1")
    private Integer id;

    @Schema(name = "name", description = "数据源名称", example = "pg_main")
    private String name;

    @Schema(name = "dbType", description = "数据库类型", example = "POSTGRESQL")
    private String dbType;

    @Schema(name = "driverClass", description = "JDBC 驱动类", example = "org.postgresql.Driver")
    private String driverClass;

    @Schema(name = "jdbcUrl", description = "JDBC URL", example = "jdbc:postgresql://127.0.0.1:5432/postgres")
    private String jdbcUrl;

    @Schema(name = "host", description = "主机地址", example = "127.0.0.1")
    private String host;

    @Schema(name = "port", description = "端口号", example = "5432")
    private Integer port;

    @Schema(name = "dbName", description = "数据库名", example = "postgres")
    private String dbName;

    @Schema(name = "schemaName", description = "Schema 名", example = "public")
    private String schemaName;

    @Schema(name = "username", description = "连接用户名", example = "postgres")
    private String username;

    @Schema(name = "description", description = "数据源描述")
    private String description;

    @Schema(name = "status", description = "状态：1 启用，0 禁用", example = "1")
    private Integer status;

    @Schema(name = "createTime", description = "创建时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    @Schema(name = "updateTime", description = "更新时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;
}
