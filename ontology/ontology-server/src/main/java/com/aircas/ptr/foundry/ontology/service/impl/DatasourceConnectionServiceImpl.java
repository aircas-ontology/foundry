package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableCommentVO;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.DatasourceConnectionMapper;
import com.aircas.ptr.foundry.ontology.service.DatasourceConnectionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@Service
@Slf4j
public class DatasourceConnectionServiceImpl
        extends ServiceImpl<DatasourceConnectionMapper, DatasourceConnection>
        implements DatasourceConnectionService {

    /** PostgreSQL / Postgres 缺省 schema。 */
    private static final String DEFAULT_SCHEMA_POSTGRES = "public";
    /** SQL Server 缺省 schema。 */
    private static final String DEFAULT_SCHEMA_SQLSERVER = "dbo";

    @Override
    public List<DatasourceConnectionVO> searchByKeyword(String keyword) {
        List<DatasourceConnection> list = baseMapper.searchByKeyword(keyword);
        return list.stream().map(this::toVO).toList();
    }

    /**
     * 扫描指定数据源的表名与表注释。
     *
     * <p>连接参数全部取自 {@code datasource_connection} 记录（{@code driverClass} / {@code jdbcUrl} /
     * {@code dbName} / {@code schemaName} / {@code username} / {@code password}），运行时按 {@code dbType}
     * 分派 {@link DatabaseMetaData#getTables} 的 {@code catalog} / {@code schema} 参数——各厂商语义不一致：
     * MySQL/MariaDB 的 database 对应 JDBC catalog；SQL Server 用 catalog=database、schema 缺省 {@code dbo}；
     * Oracle 的 schema 缺省等于登录用户名（且大写存储）；PostgreSQL 的 schema 缺省 {@code public}。</p>
     *
     * <p>{@code remarksReporting=true} 用于让 PostgreSQL 驱动把 {@code obj_description} 表注释填入 REMARKS，
     * 其他厂商驱动会忽略此属性；MySQL 若需返回表注释，须在 {@code jdbcUrl} 上加 {@code useInformationSchema=true}，
     * 该 URL 参数属于数据源配置项，不在本方法内注入。</p>
     */
    @Override
    public List<TableCommentVO> listTableComments(Integer datasourceId) {
        DatasourceConnection conn = requireDatasource(datasourceId);
        loadDriver(conn.getDriverClass());

        CatalogSchema cs = resolveCatalogSchema(conn);
        Properties props = buildConnectionProps(conn);

        List<TableCommentVO> result = new ArrayList<>();
        try (Connection dbConn = DriverManager.getConnection(conn.getJdbcUrl(), props)) {
            DatabaseMetaData metaData = dbConn.getMetaData();
            try (ResultSet rs = metaData.getTables(cs.catalog(), cs.schema(), "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    result.add(new TableCommentVO(
                            rs.getString("TABLE_NAME"),
                            rs.getString("REMARKS")));
                }
            }
        } catch (Exception e) {
            log.error("扫描数据源表注释失败，datasourceId={}, dbType={}, catalog={}, schema={}",
                    datasourceId, conn.getDbType(), cs.catalog(), cs.schema(), e);
            throw new BusinessException("扫描表注释失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return result;
    }

    /**
     * 扫描指定表的列信息（列名 + 列注释 + 数据类型 + 是否主键）。
     *
     * <p>主键集合由 {@link DatabaseMetaData#getPrimaryKeys} 单独拉取，避免在 {@code getColumns}
     * 循环里对每列反查；{@code remarksReporting=true} 仅对 PostgreSQL 生效，其他厂商需
     * 在驱动级开启（与 {@link #listTableComments(Integer)} 注释一致）。</p>
     */
    @Override
    public List<TableColumnDescVO> listTableColumns(Integer datasourceId, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new BusinessException("表名不能为空", HttpStatus.BAD_REQUEST);
        }
        DatasourceConnection conn = requireDatasource(datasourceId);
        loadDriver(conn.getDriverClass());

        CatalogSchema cs = resolveCatalogSchema(conn);
        Properties props = buildConnectionProps(conn);

        List<TableColumnDescVO> result = new ArrayList<>();
        try (Connection dbConn = DriverManager.getConnection(conn.getJdbcUrl(), props)) {
            DatabaseMetaData metaData = dbConn.getMetaData();
            Set<String> primaryKeys = readPrimaryKeys(metaData, cs, tableName);
            try (ResultSet rs = metaData.getColumns(cs.catalog(), cs.schema(), tableName, "%")) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    result.add(TableColumnDescVO.builder()
                            .columnName(columnName)
                            .description(rs.getString("REMARKS"))
                            .type(rs.getString("TYPE_NAME"))
                            .isPrimaryKey(primaryKeys.contains(columnName))
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("扫描表列信息失败，datasourceId={}, dbType={}, catalog={}, schema={}, tableName={}",
                    datasourceId, conn.getDbType(), cs.catalog(), cs.schema(), tableName, e);
            throw new BusinessException("扫描表列信息失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        // DatabaseMetaData#getColumns 已按 ORDINAL_POSITION 升序返回；此处不再重排，避免不必要开销
        return result;
    }

    /**
     * 拉取主键列名集合；部分驱动（如 SQL Server 无主键表）返回空集，不视为异常。
     */
    private Set<String> readPrimaryKeys(DatabaseMetaData metaData, CatalogSchema cs, String tableName) {
        Set<String> keys = new HashSet<>();
        try (ResultSet rs = metaData.getPrimaryKeys(cs.catalog(), cs.schema(), tableName)) {
            while (rs.next()) {
                keys.add(rs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            // 主键信息缺失不阻断列扫描；记录后返回空集即可
            log.warn("读取主键信息失败，tableName={}, msg={}", tableName, e.getMessage());
        }
        return keys;
    }

    /**
     * 校验并加载数据源记录；不存在或 JDBC URL 缺失时统一抛业务异常。
     */
    private DatasourceConnection requireDatasource(Integer datasourceId) {
        DatasourceConnection conn = baseMapper.selectById(datasourceId);
        if (conn == null) {
            throw new BusinessException("数据源不存在", HttpStatus.NOT_FOUND);
        }
        if (conn.getJdbcUrl() == null || conn.getJdbcUrl().isBlank()) {
            throw new BusinessException("数据源 JDBC URL 为空", HttpStatus.BAD_REQUEST);
        }
        return conn;
    }

    /**
     * 按 dbType 分派 catalog / schema。各厂商语义不一致：
     * MySQL/MariaDB 的 database 对应 JDBC catalog；SQL Server 用 catalog=database、schema 缺省 {@code dbo}；
     * Oracle 的 schema 缺省等于登录用户名（且大写存储）；PostgreSQL 的 schema 缺省 {@code public}。
     */
    private CatalogSchema resolveCatalogSchema(DatasourceConnection conn) {
        String dbType = conn.getDbType() == null ? "" : conn.getDbType().trim().toUpperCase();
        return switch (dbType) {
            case "MYSQL", "MARIADB" -> new CatalogSchema(conn.getDbName(), null);
            case "SQLSERVER", "MSSQL" -> new CatalogSchema(conn.getDbName(),
                    defaultIfBlank(conn.getSchemaName(), DEFAULT_SCHEMA_SQLSERVER));
            case "ORACLE" -> new CatalogSchema(null,
                    defaultIfBlank(conn.getSchemaName(),
                            conn.getUsername() == null ? null : conn.getUsername().toUpperCase()));
            case "POSTGRESQL", "POSTGRES" -> new CatalogSchema(null,
                    defaultIfBlank(conn.getSchemaName(), DEFAULT_SCHEMA_POSTGRES));
            // 未知 dbType：按 JDBC 默认语义，catalog 传 dbName（多数驱动接受），schema 传配置值
            default -> new CatalogSchema(conn.getDbName(), conn.getSchemaName());
        };
    }

    /**
     * 组装 JDBC 连接属性；{@code remarksReporting=true} 仅对 PostgreSQL 生效，其他厂商驱动忽略。
     */
    private Properties buildConnectionProps(DatasourceConnection conn) {
        Properties props = new Properties();
        if (conn.getUsername() != null) {
            props.setProperty("user", conn.getUsername());
        }
        if (conn.getPassword() != null) {
            props.setProperty("password", conn.getPassword());
        }
        props.setProperty("remarksReporting", "true");
        return props;
    }

    /**
     * catalog / schema 二元组。Java record 局部使用，避免污染包内类型。
     */
    private record CatalogSchema(String catalog, String schema) { }

    /**
     * 显式加载 JDBC 驱动类；未配置 driverClass 时直接跳过（依赖 JDBC 4+ SPI 自动注册）。
     */
    private void loadDriver(String driverClass) {
        if (driverClass == null || driverClass.isBlank()) {
            return;
        }
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new BusinessException("JDBC 驱动类未找到: " + driverClass, HttpStatus.BAD_REQUEST);
        }
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * PO -> 对外 VO，仅暴露核心字段，主动丢弃密码等敏感信息
     */
    private DatasourceConnectionVO toVO(DatasourceConnection po) {
        return new DatasourceConnectionVO(
                po.getId(),
                po.getName(),
                po.getDbType(),
                po.getHost(),
                po.getPort(),
                po.getDbName(),
                po.getSchemaName(),
                po.getDescription()
        );
    }
}
