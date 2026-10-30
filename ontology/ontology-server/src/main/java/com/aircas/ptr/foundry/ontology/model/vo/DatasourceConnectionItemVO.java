package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * 数据源连接配置管理端 VO：供数据源管理页回显完整连接信息（含 jdbcUrl / driverClass / username / status / 时间），
 * 仍不含 {@code password}——密码只接受写入，任何查询接口都不返回。
 * <p>与 {@link DatasourceConnectionVO} 分开：后者是 MCP 工具给大模型看的最小字段集，不该被连接串等噪音撑大。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "数据源连接配置VO（管理端，不含密码）")
public class DatasourceConnectionItemVO {

    @Schema(description = "数据源主键id")
    private Integer id;

    @Schema(description = "数据源名称")
    private String name;

    @Schema(description = "数据库类型（POSTGRESQL / MYSQL / ORACLE / SQLSERVER 等）")
    private String dbType;

    @Schema(description = "JDBC 驱动类全限定名")
    private String driverClass;

    @Schema(description = "完整 JDBC URL")
    private String jdbcUrl;

    @Schema(description = "主机地址")
    private String host;

    @Schema(description = "端口号")
    private Integer port;

    @Schema(description = "数据库名")
    private String dbName;

    @Schema(description = "Schema 名")
    private String schemaName;

    @Schema(description = "连接用户名")
    private String username;

    @Schema(description = "数据源描述")
    private String description;

    @Schema(description = "状态：1 启用，0 禁用")
    private Integer status;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;
}
