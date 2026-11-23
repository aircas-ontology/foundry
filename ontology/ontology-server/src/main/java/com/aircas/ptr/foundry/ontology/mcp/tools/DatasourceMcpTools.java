package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.param.DatasourceConnectionParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyBuildFromDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionItemVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceSchemaVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableSummaryVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyBuildFromDatasourceResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableCommentVO;
import com.aircas.ptr.foundry.ontology.service.DatasourceConnectionService;
import com.aircas.ptr.foundry.ontology.service.OntologyBuildFromDatasourceService;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据源 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明数据源连接配置
 * 增删改查、外部库 schema/表/列扫描、数据湖表元数据查询与删表删列、以及「数据源构建对象」原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。方法委托既有 Service，
 * 直接返回自有业务 VO（不含密码等敏感字段），Spring AI 自动序列化为 MCP 结果。</p>
 *
 * <p>MCP 调用没有 REST 侧的参数校验切面，必填项与格式在工具内自行判空防护；
 * 复杂入参（连接配置、建对象入参）以 JSON 字符串传入，用 Jackson2 反序列化为既有 Param。</p>
 */
@Component
@RequiredArgsConstructor
public class DatasourceMcpTools implements McpTools {

    private final DatasourceConnectionService datasourceConnectionService;

    private final TableMetadataService tableMetadataService;

    private final OntologyBuildFromDatasourceService ontologyBuildFromDatasourceService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            按关键词查询平台已配置的数据源（数据库连接）列表。
            当用户需要选择数据源、查看有哪些可用数据源，或询问"有哪些数据库""某某数据源是否存在"时使用。
            返回数据源的名称、类型、主机、端口、数据库名、Schema 及描述等信息（不含密码等敏感字段）。
            关键词为空时返回全部启用中的数据源。
            """)
    public List<DatasourceConnectionVO> searchDatasources(
            @ToolParam(description = "搜索关键词，可为空；按数据源名称或数据库类型模糊匹配，为空时返回全部启用数据源", required = false) String keyword) {
        return datasourceConnectionService.searchByKeyword(keyword);
    }

    @Tool(description = """
            扫描指定数据源的表注释（表名 + 表注释）。
            当用户需要基于数据源创建对象、匹配表结构时使用。
            返回该数据源下所有表的表名和表注释列表。
            支持 POSTGRESQL / MYSQL / ORACLE / SQLSERVER 等主流关系型数据库；具体驱动、URL、schema 由数据源配置决定。
            """)
    public List<TableCommentVO> scanTableComments(
            @ToolParam(description = "数据源 id（datasource_connection 表主键）") Integer datasourceId) {
        return datasourceConnectionService.listTableComments(datasourceId);
    }

    @Tool(description = """
            扫描指定数据源下指定表的列信息（列名 + 列注释 + 数据类型 + 是否主键）。
            当用户已确认对象后、需要推导该对象的属性清单时使用；支持主表与关联表的多次调用，
            以拼接来自不同表的属性。返回字段按表中物理顺序排列。
            支持 POSTGRESQL / MYSQL / ORACLE / SQLSERVER 等主流关系型数据库；列注释仅当驱动开启 remarksReporting
            或 JDBC URL 带 useInformationSchema=true 时非空（与数据源配置相关）。
            """)
    public List<TableColumnDescVO> scanTableColumns(
            @ToolParam(description = "数据源 id（datasource_connection 表主键）") Integer datasourceId,
            @ToolParam(description = "表名（区分大小写，需与 scanTableComments 返回的 tableName 一致）") String tableName) {
        return datasourceConnectionService.listTableColumns(datasourceId, tableName);
    }

    // ==================== 数据源连接配置管理（/datasource/connection） ====================

    @Tool(description = """
            分页查询数据源连接配置列表（管理端完整视图）。
            当用户需要管理数据源（查看含禁用在内的全部数据源、核对 JDBC URL / 驱动 / 用户名等连接配置）时使用；
            只需给 Agent 挑选启用数据源时用 searchDatasources 更轻量。
            参数：keyword 按名称/数据库类型模糊匹配，可为空；status 过滤状态（1=启用，0=禁用，不传查全部）；
            pageNum 页码从 1 开始（默认 1）；pageSize 每页条数（默认 20）。
            返回 MyBatis-Plus 分页对象：records 为数据源列表（含 id、spaceId、name、dbType、driverClass、jdbcUrl、
            host、port、dbName、schemaName、username、description、status、创建/更新时间），total 为总条数；
            任何查询都不返回密码。records 为空数组表示无匹配数据源。按更新时间倒序。
            """)
    public IPage<DatasourceConnectionItemVO> pageDatasources(
            @ToolParam(description = "关键词：按数据源名称或数据库类型模糊匹配，可为空", required = false) String keyword,
            @ToolParam(description = "状态过滤：1=启用，0=禁用；不传查全部", required = false) Integer status,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数，默认 20", required = false) Integer pageSize) {
        return datasourceConnectionService.pageDatasources(
                StringUtils.defaultString(keyword), status,
                pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
    }

    @Tool(description = """
            查询单个数据源连接配置的完整信息（编辑回显用）。
            当用户需要查看某个数据源的详细连接配置（JDBC URL、驱动类、用户名、schema、状态等）时使用。
            返回该数据源的完整配置 VO（含 id、spaceId、name、dbType、driverClass、jdbcUrl、host、port、
            dbName、schemaName、username、description、status、创建/更新时间），不返回密码。
            id 不存在时抛业务异常（404 语义）。
            """)
    public DatasourceConnectionItemVO getDatasourceDetail(
            @ToolParam(description = "数据源 id（datasource_connection 表主键，数字字符串）") String id) {
        return datasourceConnectionService.getDatasource(parseId(id, "数据源 id"));
    }

    @Tool(description = """
            新增数据源连接配置（写操作：向主库 ontology.datasource_connection 插入一条记录）。
            当用户要接入一个新的外部数据库时使用；建议先用 testDatasourceConnection 试连成功后再保存。
            paramJson 为 JSON 对象字符串，字段：
            - name：数据源名称，必填，最长 128 字（允许重名）；
            - dbType：数据库类型，必填，POSTGRESQL / MYSQL / MARIADB / ORACLE / SQLSERVER 等，不区分大小写（落库统一转大写）；
            - jdbcUrl：完整 JDBC URL，必填，如 jdbc:postgresql://host:5432/dbname；
            - username：连接用户名，必填；password：连接密码，新增时必填；
            - spaceId：归属对象空间 id，可选（每个空间至多一条数据源记录，重复报错）；
            - driverClass：JDBC 驱动类全限定名，可选（留空依赖驱动 SPI 自动注册）；
            - host / port / dbName：冗余展示字段，可选；schemaName：可选，留空按厂商缺省（PG=public、SQLServer=dbo、Oracle=用户名）；
            - description：描述，可选；status：1 启用 0 禁用，可选，不传按启用落库。
            id 与创建/更新时间由后台生成，不接受传入。返回落库后的完整配置 VO（不含密码）。
            """)
    public DatasourceConnectionItemVO createDatasource(
            @ToolParam(description = "数据源连接配置 JSON 对象字符串，如 {\"name\":\"生产库\",\"dbType\":\"POSTGRESQL\",\"jdbcUrl\":\"jdbc:postgresql://h:5432/db\",\"username\":\"u\",\"password\":\"p\"}") String paramJson) {
        DatasourceConnectionParam param = parseParam(paramJson, DatasourceConnectionParam.class, "paramJson");
        requireNotBlank(param.getName(), "name（数据源名称）");
        requireNotBlank(param.getDbType(), "dbType（数据库类型）");
        requireNotBlank(param.getJdbcUrl(), "jdbcUrl（JDBC URL）");
        requireNotBlank(param.getUsername(), "username（连接用户名）");
        requireNotBlank(param.getPassword(), "password（连接密码，新增必填）");
        return datasourceConnectionService.createDatasource(param);
    }

    @Tool(description = """
            修改数据源连接配置（写操作：按入参覆盖库中记录）。
            当用户要改数据源的连接地址、账号、schema、描述或启停状态时使用。
            注意覆盖语义：除密码外其余字段一律按入参覆盖，JSON 里给空值就是清空该列（如 description、schemaName），
            所以应先 getDatasourceDetail 取回现值、改动目标字段后整体传入；
            password 留空/不传表示沿用库中原密码（密码任何查询接口都不返回）。
            paramJson 字段同 createDatasource：name / dbType / jdbcUrl / username 必填，
            spaceId / driverClass / host / port / dbName / schemaName / description / status 可选。
            返回修改后的完整配置 VO（不含密码）；id 不存在时抛业务异常。
            """)
    public DatasourceConnectionItemVO updateDatasource(
            @ToolParam(description = "要修改的数据源 id（数字字符串）") String id,
            @ToolParam(description = "数据源连接配置 JSON 对象字符串（除 password 外按入参覆盖，password 留空沿用原密码）") String paramJson) {
        DatasourceConnectionParam param = parseParam(paramJson, DatasourceConnectionParam.class, "paramJson");
        requireNotBlank(param.getName(), "name（数据源名称）");
        requireNotBlank(param.getDbType(), "dbType（数据库类型）");
        requireNotBlank(param.getJdbcUrl(), "jdbcUrl（JDBC URL）");
        requireNotBlank(param.getUsername(), "username（连接用户名）");
        return datasourceConnectionService.updateDatasource(parseId(id, "数据源 id"), param);
    }

    @Tool(description = """
            删除数据源连接配置（写操作：物理删除，不可逆）。
            当用户明确要移除某个数据源配置时使用。只删除平台里的连接配置记录，不会动外部数据库本身；
            没有表引用本记录主键，删除不留悬挂引用。
            若只是临时下架，应改用 updateDatasource 把 status 置 0（禁用后 Agent 数据源检索不再返回该条），而不是删除。
            返回删除结果说明；id 不存在时抛业务异常。
            """)
    public String deleteDatasource(
            @ToolParam(description = "要删除的数据源 id（数字字符串）") String id) {
        Integer datasourceId = parseId(id, "数据源 id");
        datasourceConnectionService.deleteDatasource(datasourceId);
        return "数据源删除成功（物理删除连接配置），id=" + datasourceId;
    }

    @Tool(description = """
            列出对象空间对应数据源下的 schema 清单（实时 JDBC 扫描，只读）。
            「数据源构建对象」向导第①步：先选 schema，再列表、列列。每个空间至多绑定一条数据源记录。
            MySQL/MariaDB 上 schema 即数据库名（catalog），其余厂商为 schema；系统 schema
            （pg_*、information_schema、Oracle SYS/SYSTEM、SQLServer sys/db_*、MySQL mysql/performance_schema/sys）已过滤。
            返回 [{schemaName, catalogName}]；schemaName 即后续 listTablesBySpace / listTablesColumnsBySpace /
            buildObjectFromDatasource 要回传的参数。空数组表示无可用 schema。空间未绑定数据源时抛业务异常。
            """)
    public List<DatasourceSchemaVO> listSchemasBySpace(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId) {
        requireNonNull(spaceId, "spaceId（对象空间 id）");
        return datasourceConnectionService.listSchemas(spaceId);
    }

    @Tool(description = """
            列出对象空间对应数据源下指定 schema 的表清单（含每张表的字段数，实时 JDBC 扫描，只读）。
            「数据源构建对象」向导第②步：选表。需要知道表结构概况时用本工具；只要表名+注释的最小返回体用 scanTableComments。
            schemaName 取自 listSchemasBySpace 的返回（MySQL/MariaDB 上作为 catalog，其余厂商作为 schema）。
            返回 [{tableName, tableComment, columnCount}]；空数组表示该 schema 下无表。
            """)
    public List<DatasourceTableSummaryVO> listTablesBySpace(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId,
            @ToolParam(description = "外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）") String schemaName) {
        requireNonNull(spaceId, "spaceId（对象空间 id）");
        requireNotBlank(schemaName, "schemaName（外部库 schema 名）");
        return datasourceConnectionService.listTablesWithColumnCount(spaceId, schemaName);
    }

    @Tool(description = """
            一次扫描对象空间对应数据源下多张表的列信息与表间外键候选（实时 JDBC 扫描，只读）。
            「数据源构建对象」向导第③步：选属性、确认主子表关联。
            返回每张表 {tableName, tableComment, primaryKeyColumns, columns:[{columnName, nativeType, description,
            isPrimaryKey, ontologyDataType}], foreignKeys:[{masterTable, masterColumn, childTable, childColumn, origin}]}。
            外键来源：数据库声明的外键 origin=DECLARED；无声明时按同名 *_id 列推断，origin=HEURISTIC（需用户确认）。
            每张子表至多返回一条外键；只有传入表集合内部的表之间才会给出外键。
            tableNames 支持 JSON 数组字符串（如 ["ship","ship_weapon"]）或逗号分隔串；表名区分大小写。
            """)
    public List<DatasourceTableDetailVO> listTablesColumnsBySpace(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId,
            @ToolParam(description = "外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）") String schemaName,
            @ToolParam(description = "表名集合：JSON 数组字符串如 [\"ship\",\"ship_weapon\"]，或逗号分隔串；区分大小写") String tableNames) {
        requireNonNull(spaceId, "spaceId（对象空间 id）");
        requireNotBlank(schemaName, "schemaName（外部库 schema 名）");
        return datasourceConnectionService.listTablesDetail(spaceId, schemaName, parseTableNames(tableNames));
    }

    @Tool(description = """
            按数据源 id 列出 schema 清单（实时 JDBC 扫描，只读），行为与 listSchemasBySpace 一致，只是按数据源主键定位。
            当数据源尚未绑定对象空间（按 spaceId 查不到）时使用本工具探查。
            返回 [{schemaName, catalogName}]，系统 schema 已过滤；空数组表示无可用 schema。
            id 不存在或 JDBC URL 为空时抛业务异常。
            """)
    public List<DatasourceSchemaVO> listSchemasByDatasource(
            @ToolParam(description = "数据源 id（datasource_connection 表主键，数字字符串）") String id) {
        return datasourceConnectionService.listSchemasByDatasource(parseId(id, "数据源 id"));
    }

    @Tool(description = """
            按数据源 id + schema 列出表清单（含字段数，实时 JDBC 扫描，只读），行为与 listTablesBySpace 一致。
            当数据源尚未绑定对象空间时使用；schemaName 取自 listSchemasByDatasource 的返回。
            返回 [{tableName, tableComment, columnCount}]；空数组表示该 schema 下无表。
            """)
    public List<DatasourceTableSummaryVO> listTablesByDatasource(
            @ToolParam(description = "数据源 id（datasource_connection 表主键，数字字符串）") String id,
            @ToolParam(description = "外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）") String schemaName) {
        requireNotBlank(schemaName, "schemaName（外部库 schema 名）");
        return datasourceConnectionService.listTablesWithColumnCountByDatasource(parseId(id, "数据源 id"), schemaName);
    }

    @Tool(description = """
            按数据源 id + schema 一次扫描多张表的列信息与表间外键候选（实时 JDBC 扫描，只读），
            行为与 listTablesColumnsBySpace 一致，当数据源尚未绑定对象空间时使用。
            返回每张表 {tableName, tableComment, primaryKeyColumns, columns, foreignKeys}；
            外键 origin=DECLARED 为库中声明、HEURISTIC 为同名 *_id 列推断（需确认）；每张子表至多一条外键，
            且只给出传入表集合内部的外键。tableNames 支持 JSON 数组字符串或逗号分隔串；表名区分大小写。
            """)
    public List<DatasourceTableDetailVO> listTablesColumnsByDatasource(
            @ToolParam(description = "数据源 id（datasource_connection 表主键，数字字符串）") String id,
            @ToolParam(description = "外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）") String schemaName,
            @ToolParam(description = "表名集合：JSON 数组字符串如 [\"ship\",\"ship_weapon\"]，或逗号分隔串；区分大小写") String tableNames) {
        requireNotBlank(schemaName, "schemaName（外部库 schema 名）");
        return datasourceConnectionService.listTablesDetailByDatasource(parseId(id, "数据源 id"), schemaName,
                parseTableNames(tableNames));
    }

    @Tool(description = """
            测试数据源连通性（只读：建立一次 JDBC 连接读取数据库产品名与版本后关闭，不执行 SQL、不写库）。
            当用户保存新数据源前想先验证连接参数，或编辑已有数据源后想复测时使用。
            两种用法：
            1) 只传 id：用库中已存配置直接试连；
            2) 传 paramJson（可叠加 id）：按「表单覆盖库中记录」合并——传 id 时以该条为底、JSON 里非空字段覆盖之
               （可在不回显密码的前提下改地址复测）；不传 id 时纯用 JSON 表单试连。
            paramJson 字段同 createDatasource，但都可选（只传需要覆盖的字段即可）。id 与 paramJson 至少传一个。
            返回数据库产品名与版本字符串，如 "PostgreSQL 14.5"；连接失败抛业务异常并带原因。
            """)
    public String testDatasourceConnection(
            @ToolParam(description = "已保存的数据源 id（数字字符串），可不传", required = false) String id,
            @ToolParam(description = "连接配置 JSON 对象字符串（只传需覆盖的字段），可不传；与 id 至少传一个", required = false) String paramJson) {
        if (StringUtils.isBlank(id) && StringUtils.isBlank(paramJson)) {
            throw new IllegalArgumentException("id 与 paramJson 至少传一个：要么按已存数据源 id 试连，要么用连接表单试连");
        }
        Integer datasourceId = StringUtils.isBlank(id) ? null : parseId(id, "数据源 id");
        DatasourceConnectionParam param = StringUtils.isBlank(paramJson) ? null
                : parseParam(paramJson, DatasourceConnectionParam.class, "paramJson");
        return datasourceConnectionService.testConnection(datasourceId, param);
    }

    // ==================== 数据湖表元数据（/datasource，TableMetadataService） ====================

    @Tool(description = """
            查询数据湖中指定对象空间 schema 下某张实体表的列信息（查数据湖自身的 information_schema，只读）。
            当用户需要查看平台已建实体表（对象/子表落库后的物理表）的列名、类型、注释、是否主键时使用；
            与 scanTableColumns（扫外部数据源）不同，本工具查的是数据湖里的表。
            dataSourceId 传数据湖物理表名（即 pageDataLakeTables 返回的 tableName，通常为对象 apiName 或子表表名）。
            返回 [{columnName, description, type, isPrimaryKey}]，按物理顺序排列；空数组表示表不存在或无列。
            spaceId 无效时抛业务异常。
            """)
    public List<TableColumnDescVO> getDataLakeTableColumns(
            @ToolParam(description = "对象空间 id（spaceId），数据湖 schema 取该空间的 apiName") Integer spaceId,
            @ToolParam(description = "数据湖物理表名（pageDataLakeTables 返回的 tableName）") String dataSourceId) {
        requireNonNull(spaceId, "spaceId（对象空间 id）");
        requireNotBlank(dataSourceId, "dataSourceId（数据湖物理表名）");
        return tableMetadataService.getColumns(spaceId, dataSourceId);
    }

    @Tool(description = """
            分页搜索数据湖中指定对象空间 schema 下的实体表列表（只读）。
            当用户需要查看某空间已建了哪些实体表（对象主表、子表等物理表）时使用。
            schema 固定取该空间的 apiName，无需传 schemaName。
            参数：keyword 按表名/注释模糊匹配，可为空；pageNum 页码从 1 开始（默认 1）；pageSize 每页条数（默认 1000）。
            返回分页对象：records 为 [{schemaName, tableName, description}]，total 为总条数；
            records 为空数组表示该空间下无匹配实体表。spaceId 无效时抛业务异常。
            """)
    public Page<DatasourceTableVO> pageDataLakeTables(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId,
            @ToolParam(description = "关键词：按表名/注释模糊匹配，可为空", required = false) String keyword,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数，默认 1000", required = false) Integer pageSize) {
        requireNonNull(spaceId, "spaceId（对象空间 id）");
        return tableMetadataService.getTables(spaceId, StringUtils.defaultString(keyword),
                pageNum == null ? 1 : pageNum, pageSize == null ? 1000 : pageSize);
    }

    @Tool(description = """
            删除数据湖中的整张实体表（写操作：DROP TABLE，表结构与全部数据一并丢失，不可逆！）。
            仅当用户明确要求删除某张数据湖物理表时使用，执行前务必与用户确认表名与 schema。
            副作用：物理删表后会向消息队列发送 DROP_TABLE 表结构变更事件，通知下游数据组织层同步。
            参数：schemaName 为数据湖 schema 名（即对象空间的 apiName）；dataSourceId 为物理表名。
            返回删除结果说明。删表不影响对象元数据本身，如需连对象一起删请走对象删除接口。
            """)
    public String dropDataLakeTable(
            @ToolParam(description = "数据湖 schema 名（对象空间的 apiName）") String schemaName,
            @ToolParam(description = "要删除的数据湖物理表名") String dataSourceId) {
        requireNotBlank(schemaName, "schemaName（数据湖 schema 名）");
        requireNotBlank(dataSourceId, "dataSourceId（数据湖物理表名）");
        tableMetadataService.dropDataSource(schemaName, dataSourceId);
        return "数据湖表删除成功（DROP TABLE，不可逆），" + schemaName + "." + dataSourceId;
    }

    @Tool(description = """
            删除数据湖实体表中的若干列（写操作：ALTER TABLE DROP COLUMN，列及其数据一并丢失，不可逆！）。
            仅当用户明确要求删除某些物理列时使用，执行前务必确认列名清单。
            副作用：删列后会向消息队列发送 DELETE_COLUMN 表结构变更事件，通知下游数据组织层同步。
            参数：schemaName 为数据湖 schema 名（对象空间的 apiName）；dataSourceId 为物理表名；
            columnsJson 为列名 JSON 数组字符串，如 ["col_a","col_b"]，必填且不能为空数组。
            返回删除结果说明。删列不影响属性元数据本身。
            """)
    public String dropDataLakeColumns(
            @ToolParam(description = "数据湖 schema 名（对象空间的 apiName）") String schemaName,
            @ToolParam(description = "数据湖物理表名") String dataSourceId,
            @ToolParam(description = "要删除的列名 JSON 数组字符串，如 [\"col_a\",\"col_b\"]") String columnsJson) {
        requireNotBlank(schemaName, "schemaName（数据湖 schema 名）");
        requireNotBlank(dataSourceId, "dataSourceId（数据湖物理表名）");
        List<String> columns = parseTableNames(columnsJson);
        tableMetadataService.dropColumns(schemaName, dataSourceId, columns);
        return "数据湖表列删除成功（DROP COLUMN，不可逆），" + schemaName + "." + dataSourceId + "，列：" + columns;
    }

    // ==================== 数据源构建对象（/datasource/build_object） ====================

    @Tool(description = """
            用外部数据源勾选的表与列一次性创建一个对象（写操作：建对象元数据 + 属性 + 数据湖实体表 + 主子表列映射）。
            「数据源构建对象」向导第④步（落库）；前三步用 listSchemasBySpace / listTablesBySpace / listTablesColumnsBySpace。
            一次调用只建一个对象；多张表即「一主多子」：主表属性 storageGroup=main、实体表名取对象 apiName，
            子表属性用子表名作存储分组与实体表名。只建结构不搬数据：实体表建出来是空的，外部库实例数据不会自动灌入；
            数据管道事件也不在这里发（REST 侧由前端另调 POST /property/notify_build_pipeline）。
            paramJson 为 JSON 对象字符串，字段：
            - spaceId：对象空间 id，必填；
            - datasourceId：数据源 id（datasource_connection.id），可选：传了就用这条数据源连外部库扫列类型与列注释，
              不要求 spaceId 已绑定数据源；不传则回落到 spaceId 绑定的那条；两者都没有时跳过扫描，属性类型统一落 String；
            - schemaName：外部库 schema 名，必填（与第②③步一致，服务端会重扫列复核类型）；
            - displayName：对象中文名，必填；apiName：对象 api 名，必填，须匹配 ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$，同时作为主表物理表名；
            - categoryId：对象分类 id，可选；description：对象描述，可选；
            - tables：必填数组，恰好一张 master=true 作主表（只选一张时它即主表），元素
              {tableName（必填，区分大小写）, master（布尔）, sourceTableName（可选，溯源）, columns:[{columnName（必填，同时作为属性 apiName）,
              displayName（可选，缺省用列注释）, description（可选）, sourceColumnName（可选）}]}；
            - foreignKeys：多表时必填，每张子表恰好一条 {masterTable, masterColumn, childTable, childColumn}；
              外键列不要求勾成属性（主子表同名外键列两侧都勾成属性会撞 apiName，未勾选的一侧只建物理列）。
            返回 {uniqueIdentifier（新对象 id）, apiName, displayName, propertyCount（建成属性数）,
            entityTables（建成的实体表名清单）, mappingCount（映射行数）, message}。
            """)
    public OntologyBuildFromDatasourceResultVO buildObjectFromDatasource(
            @ToolParam(description = "建对象入参 JSON 对象字符串（含 spaceId/schemaName/displayName/apiName/tables/foreignKeys，详见工具描述）") String paramJson) {
        OntologyBuildFromDatasourceParam param = parseParam(paramJson, OntologyBuildFromDatasourceParam.class, "paramJson");
        requireNonNull(param.getSpaceId(), "spaceId（对象空间 id）");
        requireNotBlank(param.getSchemaName(), "schemaName（外部库 schema 名）");
        requireNotBlank(param.getDisplayName(), "displayName（对象名称）");
        requireNotBlank(param.getApiName(), "apiName（对象 api 名称）");
        if (param.getTables() == null || param.getTables().isEmpty()) {
            throw new IllegalArgumentException("tables 不能为空：至少勾选一张表（恰好一张 master=true 作主表）");
        }
        return ontologyBuildFromDatasourceService.buildObject(param);
    }

    // ==================== 入参解析与判空防护 ====================

    private Integer parseId(String id, String fieldDesc) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException(fieldDesc + " 不能为空");
        }
        try {
            return Integer.valueOf(id.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldDesc + " 必须是数字字符串，实际传入：" + id);
        }
    }

    private <T> T parseParam(String json, Class<T> type, String paramName) {
        if (StringUtils.isBlank(json)) {
            throw new IllegalArgumentException(paramName + " 不能为空，应为 JSON 对象字符串");
        }
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(paramName + " 解析失败，应为合法 JSON 对象：" + e.getMessage());
        }
    }

    /** 解析表名/列名集合：优先按 JSON 数组字符串，兼容逗号分隔串（与 REST 侧 query 传法一致） */
    private List<String> parseTableNames(String names) {
        if (StringUtils.isBlank(names)) {
            throw new IllegalArgumentException("名称集合不能为空，应为 JSON 数组字符串（如 [\"a\",\"b\"]）或逗号分隔串");
        }
        String trimmed = names.trim();
        List<String> result;
        if (trimmed.startsWith("[")) {
            try {
                result = jsonMapper.readValue(trimmed, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                throw new IllegalArgumentException("名称集合 JSON 解析失败，应为 [\"a\",\"b\"] 形式的字符串数组：" + e.getMessage());
            }
        } else {
            result = Arrays.stream(trimmed.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
        }
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("名称集合不能为空数组");
        }
        return result;
    }

    private void requireNotBlank(String value, String fieldDesc) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldDesc + " 不能为空");
        }
    }

    private void requireNonNull(Object value, String fieldDesc) {
        if (value == null) {
            throw new IllegalArgumentException(fieldDesc + " 不能为空");
        }
    }
}