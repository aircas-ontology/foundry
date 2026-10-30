package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.param.DatasourceConnectionParam;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionItemVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableCommentVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.DatasourceConnectionMapper;
import com.aircas.ptr.foundry.ontology.service.DatasourceConnectionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
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
    /** 新增数据源未指定 status 时的缺省值：启用。 */
    private static final int STATUS_ENABLED = 1;

    @Override
    public List<DatasourceConnectionVO> searchByKeyword(String keyword) {
        List<DatasourceConnection> list = baseMapper.searchByKeyword(keyword);
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public IPage<DatasourceConnectionItemVO> pageDatasources(String keyword, Integer status, Integer pageNum, Integer pageSize) {
        checkStatus(status);
        IPage<DatasourceConnection> page = baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<DatasourceConnection>()
                        .and(StringUtils.isNotBlank(keyword), q -> q
                                .like(DatasourceConnection::getName, keyword)
                                .or()
                                .like(DatasourceConnection::getDbType, keyword))
                        .eq(status != null, DatasourceConnection::getStatus, status)
                        .orderByDesc(DatasourceConnection::getUpdateTime));
        Page<DatasourceConnectionItemVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toItemVO).toList());
        return voPage;
    }

    @Override
    public DatasourceConnectionItemVO getDatasource(Integer id) {
        return toItemVO(requireExists(id));
    }

    @Override
    public DatasourceConnectionItemVO createDatasource(DatasourceConnectionParam param) {
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(param.getPassword()),
                "连接密码不能为空", HttpStatus.BAD_REQUEST);
        checkStatus(param.getStatus());
        DatasourceConnection po = DatasourceConnection.builder()
                .name(param.getName().trim())
                .dbType(normalizeDbType(param.getDbType()))
                // driver_class 列是 NOT NULL：留空存空串而不是 null，此时靠 JDBC 4+ 的驱动 SPI 自动注册
                .driverClass(StringUtils.defaultString(StringUtils.trim(param.getDriverClass())))
                .jdbcUrl(param.getJdbcUrl().trim())
                .host(StringUtils.trimToNull(param.getHost()))
                .port(param.getPort())
                .dbName(StringUtils.trimToNull(param.getDbName()))
                .schemaName(StringUtils.trimToNull(param.getSchemaName()))
                .username(param.getUsername().trim())
                .password(param.getPassword())
                .description(StringUtils.trimToNull(param.getDescription()))
                .status(param.getStatus() == null ? STATUS_ENABLED : param.getStatus())
                .build();
        // id 由库自增、create_time / update_time 由 OnInsertUpdateHandler 填充，均不接受请求传入
        baseMapper.insert(po);
        return toItemVO(po);
    }

    @Override
    public DatasourceConnectionItemVO updateDatasource(Integer id, DatasourceConnectionParam param) {
        requireExists(id);
        checkStatus(param.getStatus());
        // 逐列显式 set：updateById 会跳过 null，「清空描述 / 清空 schema」就写不进去；
        // 密码留空则不 set，沿用库中原值（密码不对外返回，前端回填不了）；
        // update_time 手动填——wrapper 更新没有实体对象，MetaObjectHandler 不生效
        baseMapper.update(null, new LambdaUpdateWrapper<DatasourceConnection>()
                .eq(DatasourceConnection::getId, id)
                .set(DatasourceConnection::getName, param.getName().trim())
                .set(DatasourceConnection::getDbType, normalizeDbType(param.getDbType()))
                .set(DatasourceConnection::getDriverClass, StringUtils.defaultString(StringUtils.trim(param.getDriverClass())))
                .set(DatasourceConnection::getJdbcUrl, param.getJdbcUrl().trim())
                .set(DatasourceConnection::getHost, StringUtils.trimToNull(param.getHost()))
                .set(DatasourceConnection::getPort, param.getPort())
                .set(DatasourceConnection::getDbName, StringUtils.trimToNull(param.getDbName()))
                .set(DatasourceConnection::getSchemaName, StringUtils.trimToNull(param.getSchemaName()))
                .set(DatasourceConnection::getUsername, param.getUsername().trim())
                .set(DatasourceConnection::getDescription, StringUtils.trimToNull(param.getDescription()))
                .set(param.getStatus() != null, DatasourceConnection::getStatus, param.getStatus())
                .set(StringUtils.isNotBlank(param.getPassword()), DatasourceConnection::getPassword, param.getPassword())
                .set(DatasourceConnection::getUpdateTime, new Date()));
        return toItemVO(baseMapper.selectById(id));
    }

    @Override
    public void deleteDatasource(Integer id) {
        requireExists(id);
        baseMapper.deleteById(id);
    }

    @Override
    public String testConnection(Integer id, DatasourceConnectionParam param) {
        DatasourceConnection target = resolveTestTarget(id, param);
        loadDriver(target.getDriverClass());
        Properties props = buildConnectionProps(target);
        // 只连一次读厂商自报信息，不执行任何语句、不落库
        try (Connection dbConn = DriverManager.getConnection(target.getJdbcUrl(), props)) {
            DatabaseMetaData metaData = dbConn.getMetaData();
            return metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
        } catch (Exception e) {
            log.warn("测试数据源连接失败，datasourceId={}, jdbcUrl={}", id, target.getJdbcUrl(), e);
            throw new BusinessException("连接失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
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
     * 仅校验记录存在性（增删改查与测试连接用）；JDBC URL 是否为空由 {@link #requireDatasource(Integer)}
     * 在实际要建连的扫描入口再判，避免删除一条配置残缺的记录也被拦住。
     */
    private DatasourceConnection requireExists(Integer id) {
        DatasourceConnection conn = baseMapper.selectById(id);
        PreconditionUtils.checkNotNull(conn, "数据源不存在：" + id, HttpStatus.NOT_FOUND);
        return conn;
    }

    /**
     * status 只允许 1（启用）/ 0（禁用）；null 表示「不指定」，由调用方按缺省或「不改动」处理。
     */
    private void checkStatus(Integer status) {
        PreconditionUtils.checkArgument(status == null || status == STATUS_ENABLED || status == 0,
                "status 只能是 1（启用）或 0（禁用）", HttpStatus.BAD_REQUEST);
    }

    /**
     * dbType 统一大写存储：{@link #resolveCatalogSchema(DatasourceConnection)} 按大写常量分派厂商语义，
     * 小写值会落到未知厂商的 default 分支，导致扫表拿不到 schema。
     */
    private static String normalizeDbType(String dbType) {
        return dbType.trim().toUpperCase();
    }

    /**
     * 组装测试连接的目标配置：传了 id 就以库中记录为底，请求表单里的非空字段覆盖之；
     * 只取建连真正用到的四个字段（驱动 / URL / 账号 / 密码），其余字段与试连无关。
     */
    private DatasourceConnection resolveTestTarget(Integer id, DatasourceConnectionParam param) {
        DatasourceConnection stored = id == null ? null : requireExists(id);
        if (stored == null && param == null) {
            throw new BusinessException("请提交连接表单，或传入已保存的数据源 id", HttpStatus.BAD_REQUEST);
        }
        DatasourceConnectionParam form = param == null ? new DatasourceConnectionParam() : param;
        DatasourceConnection target = DatasourceConnection.builder()
                .driverClass(pick(form.getDriverClass(), stored == null ? null : stored.getDriverClass()))
                .jdbcUrl(pick(form.getJdbcUrl(), stored == null ? null : stored.getJdbcUrl()))
                .username(pick(form.getUsername(), stored == null ? null : stored.getUsername()))
                .password(pick(form.getPassword(), stored == null ? null : stored.getPassword()))
                .build();
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(target.getJdbcUrl()),
                "JDBC URL 不能为空", HttpStatus.BAD_REQUEST);
        return target;
    }

    /**
     * 请求值非空则用请求值（去首尾空格），否则回落到库中值——编辑场景密码回不了填就靠这里。
     */
    private static String pick(String requestValue, String storedValue) {
        return StringUtils.isNotBlank(requestValue) ? requestValue.trim() : storedValue;
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
     * PO -> 对外 VO，仅暴露核心字段，主动丢弃密码等敏感信息。
     * 采用 Builder 避免 positional 构造：后续 VO 新加字段时不会静默错位。
     */
    private DatasourceConnectionVO toVO(DatasourceConnection po) {
        return DatasourceConnectionVO.builder()
                .id(po.getId())
                .name(po.getName())
                .dbType(po.getDbType())
                .host(po.getHost())
                .port(po.getPort())
                .dbName(po.getDbName())
                .schemaName(po.getSchemaName())
                .description(po.getDescription())
                .build();
    }

    /**
     * PO -> 管理端 VO：数据源管理页要编辑连接串、驱动、账号与启停状态，所以除密码外全量返回。
     * 与 {@link #toVO(DatasourceConnection)} 分开维护——后者是给大模型的 MCP 最小字段集，
     * 不该被 jdbcUrl 这类长串撑大；两者共同点是都不返回 {@code password}。
     */
    private DatasourceConnectionItemVO toItemVO(DatasourceConnection po) {
        return DatasourceConnectionItemVO.builder()
                .id(po.getId())
                .name(po.getName())
                .dbType(po.getDbType())
                .driverClass(po.getDriverClass())
                .jdbcUrl(po.getJdbcUrl())
                .host(po.getHost())
                .port(po.getPort())
                .dbName(po.getDbName())
                .schemaName(po.getSchemaName())
                .username(po.getUsername())
                .description(po.getDescription())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
