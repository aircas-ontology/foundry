package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 数据源连接配置入参：只声明用户可写的字段，ID 由库自增、创建/更新时间由 OnInsertUpdateHandler 填充，不接受请求传入。
 * <p>{@code password} 新增必填、修改可留空（留空表示沿用库中原密码）；{@code /test} 接口不做 @Valid 校验，
 * 允许只传需要覆盖的字段。</p>
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据源连接配置入参")
public class DatasourceConnectionParam {

    @Schema(name = "name", description = "数据源名称（允许重复）", example = "生产订单库")
    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 128, message = "数据源名称最长 128 字")
    private String name;

    @Schema(name = "dbType", description = "数据库类型：POSTGRESQL / MYSQL / MARIADB / ORACLE / SQLSERVER 等，不区分大小写",
            example = "POSTGRESQL")
    @NotBlank(message = "数据库类型不能为空")
    @Size(max = 32, message = "数据库类型最长 32 字")
    private String dbType;

    @Schema(name = "driverClass", description = "JDBC 驱动类全限定名；留空则依赖驱动 SPI 自动注册",
            example = "org.postgresql.Driver")
    @Size(max = 255, message = "驱动类名最长 255 字")
    private String driverClass;

    @Schema(name = "jdbcUrl", description = "完整 JDBC URL，运行时据此建立连接",
            example = "jdbc:postgresql://172.16.18.58:35432/entity_datasource")
    @NotBlank(message = "JDBC URL 不能为空")
    @Size(max = 1024, message = "JDBC URL 最长 1024 字")
    private String jdbcUrl;

    @Schema(name = "host", description = "主机地址（冗余字段，便于列表展示）", example = "172.16.18.58")
    @Size(max = 255, message = "主机地址最长 255 字")
    private String host;

    @Schema(name = "port", description = "端口号（冗余字段）", example = "35432")
    private Integer port;

    @Schema(name = "dbName", description = "数据库名（MySQL 等以 catalog 语义扫表时使用）", example = "entity_datasource")
    @Size(max = 128, message = "数据库名最长 128 字")
    private String dbName;

    @Schema(name = "schemaName", description = "Schema 名；留空时按 dbType 取厂商缺省（PG=public、SQLServer=dbo、Oracle=用户名）",
            example = "public")
    @Size(max = 128, message = "Schema 名最长 128 字")
    private String schemaName;

    @Schema(name = "username", description = "连接用户名", example = "postgres")
    @NotBlank(message = "连接用户名不能为空")
    @Size(max = 128, message = "连接用户名最长 128 字")
    private String username;

    @Schema(name = "password", description = "连接密码；新增必填，修改时留空表示不改动库中已存密码", example = "123456")
    @Size(max = 255, message = "连接密码最长 255 字")
    private String password;

    @Schema(name = "description", description = "数据源描述", example = "订单中心主库，只读账号")
    @Size(max = 512, message = "数据源描述最长 512 字")
    private String description;

    @Schema(name = "status", description = "状态：1 启用，0 禁用；不传默认 1。禁用后 Agent 数据源检索不再返回该条", example = "1")
    private Integer status;
}
