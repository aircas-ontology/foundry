package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * 数据源连接配置
 * 对应 entity_datasource.public.datasource_connection 表（datalake 数据源，默认 public schema），
 * 保存外部数据源的连接信息，运行时可依据此表动态构建 DataSource 并连接。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@TableName(value = "datasource_connection")
public class DatasourceConnection {

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 数据源名称，全局唯一
     */
    private String name;

    /**
     * 数据库类型：POSTGRESQL / MYSQL / ORACLE / SQLSERVER 等
     */
    private String dbType;

    /**
     * JDBC 驱动类全限定名
     */
    private String driverClass;

    /**
     * 完整 JDBC URL
     */
    private String jdbcUrl;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口号
     */
    private Integer port;

    /**
     * 数据库名
     */
    private String dbName;

    /**
     * Schema 名
     */
    private String schemaName;

    /**
     * 连接用户名
     */
    private String username;

    /**
     * 连接密码（当前明文存储）
     */
    private String password;

    /**
     * 描述
     */
    private String description;

    /**
     * 状态：1 启用，0 禁用
     */
    private Integer status;

    /**
     * 记录创建时间
     */
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 记录修改时间
     */
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
