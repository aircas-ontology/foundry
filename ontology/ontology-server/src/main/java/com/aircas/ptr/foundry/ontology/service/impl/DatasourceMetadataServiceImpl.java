package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.dto.ColumnMetaDTO;
import com.aircas.ptr.foundry.ontology.model.dto.ForeignKeyDTO;
import com.aircas.ptr.foundry.ontology.model.dto.TableMetaDTO;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.service.DatasourceMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * PostgreSQL 元数据查询实现。
 * <p>
 * 说明：
 * 1. 采用 DriverManager 建立短连接 + try-with-resources 释放，适合低频元数据查询场景；
 *    如后续调用频繁可改为 HikariDataSource 池化并按 datasourceId 缓存。
 * 2. 仅支持 dbType=POSTGRESQL，其他类型直接抛异常。
 */
@Service
@Slf4j
public class DatasourceMetadataServiceImpl implements DatasourceMetadataService {

    private static final String DB_TYPE_POSTGRESQL = "POSTGRESQL";
    private static final String DEFAULT_SCHEMA = "public";
    /** 建立连接超时时间（秒），避免目标库不通时长时间挂起 */
    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    // ---------------------------------------------------------------------
    // SQL 模板（PostgreSQL）
    // ---------------------------------------------------------------------

    /** 查询指定 schema 下的普通表（relkind='r'）及表注释 */
    private static final String SQL_LIST_TABLES = """
            SELECT c.relname AS table_name,
                   obj_description(c.oid) AS table_comment
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind = 'r'
              AND n.nspname = ?
            ORDER BY c.relname
            """;

    /** 查询指定表的字段：字段名、类型、注释、是否主键 */
    private static final String SQL_LIST_COLUMNS = """
            SELECT a.attname                                        AS column_name,
                   format_type(a.atttypid, a.atttypmod)               AS data_type,
                   col_description(a.attrelid, a.attnum)              AS column_comment,
                   a.attnum                                           AS ordinal,
                   COALESCE(pk.is_pk, false)                          AS is_primary_key
            FROM pg_attribute a
            LEFT JOIN (
                SELECT i.indrelid, unnest(i.indkey) AS attnum, true AS is_pk
                FROM pg_index i
                WHERE i.indisprimary
            ) pk ON pk.indrelid = a.attrelid AND pk.attnum = a.attnum
            WHERE a.attrelid = (quote_ident(?) || '.' || quote_ident(?))::regclass
              AND a.attnum > 0
              AND NOT a.attisdropped
            ORDER BY a.attnum
            """;

    /** 查询指定 schema 下的所有外键关系 */
    private static final String SQL_LIST_FOREIGN_KEYS = """
            SELECT tc.table_schema  AS from_schema,
                   tc.table_name    AS from_table,
                   kcu.column_name  AS from_column,
                   ccu.table_schema AS to_schema,
                   ccu.table_name   AS to_table,
                   ccu.column_name  AS to_column
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_schema    = kcu.table_schema
            JOIN information_schema.constraint_column_usage ccu
              ON ccu.constraint_name = tc.constraint_name
             AND ccu.table_schema    = tc.table_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_schema = ?
            """;

    // ---------------------------------------------------------------------
    // 公共实现
    // ---------------------------------------------------------------------

    @Override
    public List<TableMetaDTO> listTables(DatasourceConnection conn, String schemaName) {
        String schema = resolveSchema(conn, schemaName);
        List<TableMetaDTO> result = new ArrayList<>();
        try (Connection c = openConnection(conn);
             PreparedStatement ps = c.prepareStatement(SQL_LIST_TABLES)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(TableMetaDTO.builder()
                            .schemaName(schema)
                            .tableName(rs.getString("table_name"))
                            .tableComment(rs.getString("table_comment"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("查询表列表失败，datasourceId={}, schema={}", conn.getId(), schema, e);
            throw new BusinessException("查询数据源表列表失败: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        return result;
    }

    @Override
    public List<ColumnMetaDTO> listColumns(DatasourceConnection conn, String schemaName, String tableName) {
        String schema = resolveSchema(conn, schemaName);
        List<ColumnMetaDTO> result = new ArrayList<>();
        try (Connection c = openConnection(conn);
             PreparedStatement ps = c.prepareStatement(SQL_LIST_COLUMNS)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(ColumnMetaDTO.builder()
                            .columnName(rs.getString("column_name"))
                            .dataType(rs.getString("data_type"))
                            .columnComment(rs.getString("column_comment"))
                            .ordinal(rs.getInt("ordinal"))
                            .primaryKey(rs.getBoolean("is_primary_key"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("查询表字段失败，datasourceId={}, schema={}, table={}",
                    conn.getId(), schema, tableName, e);
            throw new BusinessException("查询表字段失败: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        return result;
    }

    @Override
    public List<ForeignKeyDTO> listForeignKeys(DatasourceConnection conn, String schemaName) {
        String schema = resolveSchema(conn, schemaName);
        List<ForeignKeyDTO> result = new ArrayList<>();
        try (Connection c = openConnection(conn);
             PreparedStatement ps = c.prepareStatement(SQL_LIST_FOREIGN_KEYS)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(ForeignKeyDTO.builder()
                            .fromSchema(rs.getString("from_schema"))
                            .fromTable(rs.getString("from_table"))
                            .fromColumn(rs.getString("from_column"))
                            .toSchema(rs.getString("to_schema"))
                            .toTable(rs.getString("to_table"))
                            .toColumn(rs.getString("to_column"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("查询外键关系失败，datasourceId={}, schema={}", conn.getId(), schema, e);
            throw new BusinessException("查询外键关系失败: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        return result;
    }

    @Override
    public boolean testConnection(DatasourceConnection conn) {
        try (Connection c = openConnection(conn)) {
            return c.isValid(LOGIN_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            log.warn("测试连接失败，datasourceId={}, name={}, err={}",
                    conn.getId(), conn.getName(), e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // 私有工具
    // ---------------------------------------------------------------------

    /**
     * 建立 JDBC 短连接。当前仅支持 PostgreSQL。
     */
    private Connection openConnection(DatasourceConnection conn) throws SQLException {
        validate(conn);
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        Properties props = new Properties();
        props.setProperty("user", conn.getUsername());
        props.setProperty("password", conn.getPassword() == null ? "" : conn.getPassword());
        // 只读会话，避免误操作目标库
        props.setProperty("readOnly", "true");
        return DriverManager.getConnection(conn.getJdbcUrl(), props);
    }

    private void validate(DatasourceConnection conn) {
        if (conn == null) {
            throw new BusinessException("数据源连接配置不存在", HttpStatus.BAD_REQUEST);
        }
        if (!DB_TYPE_POSTGRESQL.equalsIgnoreCase(conn.getDbType())) {
            throw new BusinessException(
                    "当前仅支持 POSTGRESQL 类型的数据源，实际 dbType=" + conn.getDbType(),
                    HttpStatus.BAD_REQUEST);
        }
        if (StringUtils.isBlank(conn.getJdbcUrl())) {
            throw new BusinessException("数据源 jdbcUrl 为空", HttpStatus.BAD_REQUEST);
        }
        if (StringUtils.isBlank(conn.getUsername())) {
            throw new BusinessException("数据源 username 为空", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * schema 解析优先级：入参 > 数据源配置 > "public"
     */
    private String resolveSchema(DatasourceConnection conn, String schemaName) {
        if (StringUtils.isNotBlank(schemaName)) {
            return schemaName;
        }
        if (conn != null && StringUtils.isNotBlank(conn.getSchemaName())) {
            return conn.getSchemaName();
        }
        return DEFAULT_SCHEMA;
    }
}
