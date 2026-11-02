package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.param.DatasourceConnectionParam;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionItemVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableSummaryVO;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

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
    /**
     * 允许把 {@code getColumns} 的表名参数传 null 一次统计全库字段数的厂商。
     * Oracle / SQL Server 未列入：前者可能退化成整库字典扫描，行为未经实测验证。
     */
    private static final Set<String> WILDCARD_COLUMN_SCAN_DB_TYPES =
            Set.of("POSTGRESQL", "POSTGRES", "MYSQL", "MARIADB");
    /** 启发式外键的后缀约定：真实库普遍不建外键约束，靠 {@code xxx_id} 命名找主子表关系。 */
    private static final String ID_COLUMN_SUFFIX = "_id";

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
     * 表清单 + 字段数：一次连接内 {@code getTables} 拿表、{@code getColumns} 统计列数。
     *
     * <p>{@code getColumns} 的表名传 {@code null} 表示不按表名过滤，是 JDBC 规范允许写法，
     * 但各驱动实现不一（Oracle 上可能退化成全库字典扫描），所以只对白名单厂商走通配，
     * 其余厂商在同一连接上逐表统计——仍是连接数 1，不退化成 N+1 建连。</p>
     */
    @Override
    public List<DatasourceTableSummaryVO> listTablesWithColumnCount(Integer datasourceId) {
        DatasourceConnection conn = requireDatasource(datasourceId);
        loadDriver(conn.getDriverClass());

        CatalogSchema cs = resolveCatalogSchema(conn);
        Properties props = buildConnectionProps(conn);

        try (Connection dbConn = DriverManager.getConnection(conn.getJdbcUrl(), props)) {
            DatabaseMetaData metaData = dbConn.getMetaData();
            List<DatasourceTableSummaryVO> tables = readTables(metaData, cs);
            Map<String, Integer> counts = countColumnsByTable(metaData, cs, conn.getDbType(), tables);
            tables.forEach(t -> t.setColumnCount(counts.getOrDefault(t.getTableName(), 0)));
            return tables;
        } catch (Exception e) {
            log.error("扫描数据源表清单失败，datasourceId={}, dbType={}, catalog={}, schema={}",
                    datasourceId, conn.getDbType(), cs.catalog(), cs.schema(), e);
            throw new BusinessException("扫描表清单失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 多表列信息与表间外键。外键只给「本次勾选表集合」内部的关系，且每张子表至多一条
     * （实体表映射 {@code table_field_mapping} 的唯一约束就是主表名 + 子表名）。
     */
    @Override
    public List<DatasourceTableDetailVO> listTablesDetail(Integer datasourceId, List<String> tableNames) {
        PreconditionUtils.checkArgument(CollectionUtils.isNotEmpty(tableNames),
                "tableNames 不能为空", HttpStatus.BAD_REQUEST);
        List<String> names = tableNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        PreconditionUtils.checkArgument(!names.isEmpty(), "tableNames 不能为空", HttpStatus.BAD_REQUEST);

        DatasourceConnection conn = requireDatasource(datasourceId);
        loadDriver(conn.getDriverClass());

        CatalogSchema cs = resolveCatalogSchema(conn);
        Properties props = buildConnectionProps(conn);

        try (Connection dbConn = DriverManager.getConnection(conn.getJdbcUrl(), props)) {
            DatabaseMetaData metaData = dbConn.getMetaData();
            Map<String, String> comments = new HashMap<>();
            readTables(metaData, cs).forEach(t -> comments.put(t.getTableName(), t.getTableComment()));

            Map<String, TableShape> shapes = new LinkedHashMap<>();
            for (String name : names) {
                List<String> pkColumns = readPrimaryKeyColumnList(metaData, cs, name);
                List<DatasourceTableDetailVO.Column> columns = readColumns(metaData, cs, name, pkColumns);
                shapes.put(name, new TableShape(name, comments.get(name), pkColumns, columns));
            }
            Map<String, DatasourceTableDetailVO.ForeignKey> childToForeignKey = detectForeignKeys(cs, shapes, metaData);

            return shapes.values().stream()
                    .map(shape -> {
                        DatasourceTableDetailVO.ForeignKey foreignKey = childToForeignKey.get(shape.tableName());
                        return DatasourceTableDetailVO.builder()
                                .tableName(shape.tableName())
                                .tableComment(shape.comment())
                                .primaryKeyColumns(shape.primaryKeyColumns())
                                .columns(shape.columns())
                                .foreignKeys(foreignKey == null ? List.of() : List.of(foreignKey))
                                .build();
                    })
                    .toList();
        } catch (Exception e) {
            log.error("扫描数据源表结构失败，datasourceId={}, dbType={}, catalog={}, schema={}, tableNames={}",
                    datasourceId, conn.getDbType(), cs.catalog(), cs.schema(), names, e);
            throw new BusinessException("扫描表结构失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 读表名与表注释；{@code remarksReporting} 只在 PostgreSQL 生效，MySQL 需 jdbcUrl 上带
     * {@code useInformationSchema=true}，注释为空属于数据源配置问题，不阻断扫描。
     */
    private List<DatasourceTableSummaryVO> readTables(DatabaseMetaData metaData, CatalogSchema cs) throws SQLException {
        List<DatasourceTableSummaryVO> tables = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(cs.catalog(), cs.schema(), "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(DatasourceTableSummaryVO.builder()
                        .tableName(rs.getString("TABLE_NAME"))
                        .tableComment(rs.getString("REMARKS"))
                        .build());
            }
        }
        return tables;
    }

    /**
     * 统计每张表的字段数：白名单厂商走通配表名一次拿全，其余或通配失败时逐表回落。
     */
    private Map<String, Integer> countColumnsByTable(DatabaseMetaData metaData, CatalogSchema cs, String dbType,
                                                     List<DatasourceTableSummaryVO> tables) {
        Map<String, Integer> counts = new HashMap<>();
        String normalized = dbType == null ? "" : dbType.trim().toUpperCase();
        if (WILDCARD_COLUMN_SCAN_DB_TYPES.contains(normalized)) {
            try (ResultSet rs = metaData.getColumns(cs.catalog(), cs.schema(), null, "%")) {
                while (rs.next()) {
                    counts.merge(rs.getString("TABLE_NAME"), 1, Integer::sum);
                }
            } catch (Exception e) {
                log.warn("通配表名统计字段数失败，回落逐表统计，catalog={}, schema={}, msg={}",
                        cs.catalog(), cs.schema(), e.getMessage());
                counts.clear();
            }
            // 一张都没数到才回落：该驱动不接受 null 表名
            if (!counts.isEmpty()) {
                log.info("字段数统计走通配表名，dbType={}, 表数={}, 列数={}", normalized, tables.size(), counts.size());
                return counts;
            }
        }
        log.info("字段数统计走逐表扫描，dbType={}, 表数={}", normalized, tables.size());
        for (DatasourceTableSummaryVO table : tables) {
            try (ResultSet rs = metaData.getColumns(cs.catalog(), cs.schema(), table.getTableName(), "%")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                counts.put(table.getTableName(), count);
            } catch (Exception e) {
                log.warn("统计字段数失败，tableName={}, msg={}", table.getTableName(), e.getMessage());
            }
        }
        return counts;
    }

    /**
     * 读列清单，顺带按主键列集合标记 {@code isPrimaryKey}，并把原生类型归一化成属性类型，
     * 让向导页看到的类型与第④步真正落库的类型一致。
     */
    private List<DatasourceTableDetailVO.Column> readColumns(DatabaseMetaData metaData, CatalogSchema cs,
                                                             String tableName, List<String> pkColumns) throws SQLException {
        Set<String> pkSet = new HashSet<>(pkColumns);
        List<DatasourceTableDetailVO.Column> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(cs.catalog(), cs.schema(), tableName, "%")) {
            while (rs.next()) {
                String nativeType = rs.getString("TYPE_NAME");
                OntologyDataTypeEnum dataType = OntologyDataTypeEnum.valueOfJdbcType(nativeType);
                columns.add(DatasourceTableDetailVO.Column.builder()
                        .columnName(rs.getString("COLUMN_NAME"))
                        .nativeType(nativeType)
                        .description(rs.getString("REMARKS"))
                        .isPrimaryKey(pkSet.contains(rs.getString("COLUMN_NAME")))
                        .ontologyDataType(dataType.name())
                        .build());
            }
        }
        return columns;
    }

    /**
     * 按 {@code KEY_SEQ} 顺序返回主键列，复合主键在这里能一眼看出来（第④步不支持复合主键做外键）。
     */
    private List<String> readPrimaryKeyColumnList(DatabaseMetaData metaData, CatalogSchema cs, String tableName) {
        Set<String> ordered = new LinkedHashSet<>();
        try (ResultSet rs = metaData.getPrimaryKeys(cs.catalog(), cs.schema(), tableName)) {
            while (rs.next()) {
                ordered.add(rs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            log.warn("读取主键信息失败，tableName={}, msg={}", tableName, e.getMessage());
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 子表 → 主表 外键：先取数据库声明的外键，缺哪张子表再用同名列启发式补，补出来的标 HEURISTIC。
     */
    private Map<String, DatasourceTableDetailVO.ForeignKey> detectForeignKeys(
            CatalogSchema cs, Map<String, TableShape> shapes, DatabaseMetaData metaData) {
        Map<String, DatasourceTableDetailVO.ForeignKey> result = new LinkedHashMap<>();
        for (String child : shapes.keySet()) {
            DatasourceTableDetailVO.ForeignKey declared = findDeclaredForeignKey(cs, shapes, metaData, child);
            if (declared != null) {
                result.put(child, declared);
            }
        }
        for (String child : shapes.keySet()) {
            if (result.containsKey(child)) {
                continue;
            }
            DatasourceTableDetailVO.ForeignKey heuristic = guessForeignKeyByColumn(shapes, child);
            if (heuristic != null) {
                result.put(child, heuristic);
            }
        }
        return result;
    }

    /**
     * 声明外键：{@code getImportedKeys} 返回的子表→父表行，按父表分组后只取单列外键。
     * 一张子表被多条外键指向多个勾选主表时，优先取「主表名是子表名前缀」的那条，其余丢弃并记日志
     * ——实体表映射每对主子表只能存一条，多给前端也没法落。
     */
    private DatasourceTableDetailVO.ForeignKey findDeclaredForeignKey(CatalogSchema cs, Map<String, TableShape> shapes,
                                                                      DatabaseMetaData metaData, String child) {
        Map<String, List<String[]>> pairsByMaster = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getImportedKeys(cs.catalog(), cs.schema(), child)) {
            while (rs.next()) {
                String master = rs.getString("PK_TABLE_NAME");
                if (!shapes.containsKey(master) || master.equals(child)) {
                    continue;
                }
                pairsByMaster.computeIfAbsent(master, k -> new ArrayList<>())
                        .add(new String[]{rs.getString("PKCOLUMN_NAME"), rs.getString("FKCOLUMN_NAME")});
            }
        } catch (Exception e) {
            log.warn("读取声明外键失败，tableName={}, msg={}", child, e.getMessage());
            return null;
        }
        List<String> singleColumnMasters = pairsByMaster.entrySet().stream()
                .filter(entry -> entry.getValue().size() == 1)
                .map(Map.Entry::getKey)
                .toList();
        if (singleColumnMasters.isEmpty()) {
            if (!pairsByMaster.isEmpty()) {
                log.info("子表{}的声明外键均为复合外键，第④步不支持，已忽略：{}", child, pairsByMaster.keySet());
            }
            return null;
        }
        if (singleColumnMasters.size() > 1) {
            log.info("子表{}存在多条单列声明外键，保留与子表名最接近的主表{}，候选{}",
                    child, pickMasterByNamePrefix(child, singleColumnMasters), singleColumnMasters);
        }
        String master = pickMasterByNamePrefix(child, singleColumnMasters);
        String[] pair = pairsByMaster.get(master).get(0);
        return DatasourceTableDetailVO.ForeignKey.builder()
                .masterTable(master)
                .masterColumn(pair[0])
                .childTable(child)
                .childColumn(pair[1])
                .origin(DatasourceTableDetailVO.ForeignKeyOrigin.DECLARED)
                .build();
    }

    /**
     * 启发式外键：真实库常常不建外键约束，靠「同名列 + 是主表主键 + 不是子表主键 + 整型」三条硬条件挑候选。
     * 主表名等于列名去掉 {@code _id} 后缀时得分最高（{@code ship} ↔ {@code ship_id}）。
     */
    private DatasourceTableDetailVO.ForeignKey guessForeignKeyByColumn(Map<String, TableShape> shapes, String child) {
        TableShape childShape = shapes.get(child);
        Set<String> childPk = new HashSet<>(childShape.primaryKeyColumns());
        Map<String, String> childColumnTypes = childShape.columns().stream()
                .collect(Collectors.toMap(DatasourceTableDetailVO.Column::getColumnName,
                        c -> StringUtils.defaultString(c.getNativeType()), (a, b) -> a));

        DatasourceTableDetailVO.ForeignKey best = null;
        int bestScore = 0;
        for (TableShape master : shapes.values()) {
            if (master.tableName().equals(child)) {
                continue;
            }
            Set<String> masterPk = new HashSet<>(master.primaryKeyColumns());
            for (String column : childColumnTypes.keySet()) {
                boolean inMaster = master.columns().stream().anyMatch(c -> column.equals(c.getColumnName()));
                if (!inMaster || !StringUtils.endsWithIgnoreCase(column, ID_COLUMN_SUFFIX)) {
                    continue;
                }
                boolean namedByMaster = StringUtils.endsWithIgnoreCase(column, master.tableName() + ID_COLUMN_SUFFIX);
                boolean isMasterPk = masterPk.contains(column);
                // 至少要满足「按主表名命名」或「是主表主键」之一，否则两条无关同名列也会被判成主子表
                if (!namedByMaster && !isMasterPk) {
                    continue;
                }
                if (childPk.contains(column) || !isIntegerish(childColumnTypes.get(column))) {
                    continue;
                }
                int score = (namedByMaster ? 2 : 0) + (isMasterPk ? 1 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = DatasourceTableDetailVO.ForeignKey.builder()
                            .masterTable(master.tableName())
                            .masterColumn(column)
                            .childTable(child)
                            .childColumn(column)
                            .origin(DatasourceTableDetailVO.ForeignKeyOrigin.HEURISTIC)
                            .build();
                }
            }
        }
        return best;
    }

    /**
     * 主表名是子表名前缀的优先（{@code ship} 对 {@code ship_weapon}），都没有就取第一个，保证结果稳定。
     */
    private String pickMasterByNamePrefix(String child, List<String> masters) {
        for (String master : masters) {
            if (StringUtils.startsWithIgnoreCase(child, master)) {
                return master;
            }
        }
        return masters.get(0);
    }

    private boolean isIntegerish(String nativeType) {
        OntologyDataTypeEnum dataType = OntologyDataTypeEnum.valueOfJdbcType(nativeType);
        return dataType == OntologyDataTypeEnum.Int
                || dataType == OntologyDataTypeEnum.Long
                || dataType == OntologyDataTypeEnum.Short
                || dataType == OntologyDataTypeEnum.Byte;
    }

    /**
     * 单张外部表的结构快照，避免在多处重复传四个集合。
     */
    private record TableShape(String tableName,
                              String comment,
                              List<String> primaryKeyColumns,
                              List<DatasourceTableDetailVO.Column> columns) { }

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
