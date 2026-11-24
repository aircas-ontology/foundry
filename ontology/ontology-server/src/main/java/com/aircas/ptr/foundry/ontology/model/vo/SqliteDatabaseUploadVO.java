package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * SQLite 数据库文件上传结果：{@code jdbcUrl} 原样填进 {@code POST /datasource/connection} 即可保存数据源。
 * <p>文件落在服务端本地磁盘，所以这条连接串只在收到上传请求的那台机器上有效。</p>
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SQLite 数据库文件上传结果")
public class SqliteDatabaseUploadVO {

    @Schema(name = "jdbcUrl", description = "可直接提交的 JDBC 连接串，如 jdbc:sqlite:/sqlite-data/xxx-app.db")
    private String jdbcUrl;

    @Schema(name = "schemaName", description = "向导第②③步要回传的 schema 名；SQLite 固定 main（驱动本身不按它过滤）",
            example = "main")
    private String schemaName;

    @Schema(name = "path", description = "数据库文件在服务端落地的绝对路径")
    private String path;

    @Schema(name = "fileName", description = "上传时的原始文件名")
    private String fileName;

    @Schema(name = "size", description = "文件字节数")
    private Long size;

    @Schema(name = "tableCount", description = "上传后立即扫到的表数量，0 表示这是个能连上但没有用户表的库")
    private Integer tableCount;
}
