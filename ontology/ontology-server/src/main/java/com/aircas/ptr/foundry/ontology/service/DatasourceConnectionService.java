package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.DatasourceConnectionParam;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionItemVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceSchemaVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableSummaryVO;
import com.aircas.ptr.foundry.ontology.model.vo.SqliteDatabaseUploadVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableCommentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 数据源连接配置服务
 */
public interface DatasourceConnectionService extends IService<DatasourceConnection> {

    /**
     * 按关键词查询启用中（status=1）的数据源连接配置。
     *
     * @param keyword 搜索关键词，可为空（空则返回全部启用数据源）；按名称 / 数据库类型模糊匹配
     * @return 数据源列表（仅暴露 Agent 所需核心字段，不含密码等敏感信息）
     */
    List<DatasourceConnectionVO> searchByKeyword(String keyword);

    /**
     * 分页查询数据源连接配置（管理端列表，含 jdbcUrl / driverClass / username 等完整字段，不含密码）。
     *
     * @param keyword  关键词，可为空；按名称 / 数据库类型模糊匹配
     * @param status   状态过滤，可为空（空则启用与禁用都返回）
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果，按更新时间倒序
     */
    IPage<DatasourceConnectionItemVO> pageDatasources(String keyword, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 查询单条数据源连接配置（管理端编辑表单回显用，不含密码）。
     *
     * @param id datasource_connection 表主键
     * @return 数据源配置；不存在时抛 404 业务异常
     */
    DatasourceConnectionItemVO getDatasource(Integer id);

    /**
     * 新增数据源连接配置。
     *
     * <p>id 由库自增、create_time / update_time 由 {@code OnInsertUpdateHandler} 填充，都不取自请求；
     * {@code status} 不传按 1（启用）落库，{@code dbType} 统一转大写存储
     * （扫表时按 dbType 分派 catalog / schema，小写值会落到未知厂商分支）。</p>
     *
     * <p>{@code username} / {@code password} 必填性按厂商分派：除 SQLITE 外必填，
     * SQLITE 按文件建连、无账号密码概念，传了也不取，两列统一落空串（列本身 NOT NULL）。</p>
     *
     * @param param 连接配置入参
     * @return 落库后的完整配置（不含密码）
     */
    DatasourceConnectionItemVO createDatasource(DatasourceConnectionParam param);

    /**
     * 修改数据源连接配置。
     *
     * <p>除密码外，其余字段一律按入参覆盖——入参里给空值就是清空该列（如描述、schema）。
     * {@code password} 留空表示沿用库中原密码：密码任何查询接口都不返回，若强制必填就无法保存编辑。
     * dbType 为 SQLITE 时 {@code username} 不参与必填校验、直接覆盖为空串。</p>
     *
     * @param id    datasource_connection 表主键
     * @param param 连接配置入参
     * @return 修改后的完整配置（不含密码）
     */
    DatasourceConnectionItemVO updateDatasource(Integer id, DatasourceConnectionParam param);

    /**
     * 删除数据源连接配置（物理删除）。
     *
     * <p>没有表存本表主键做外键：Agent 会话携带的 datasourceId 只活在当次对话上下文里，不落库，
     * 因此删除不留悬挂引用。只是想临时下架某个数据源，应改 status=0 而不是删。</p>
     *
     * @param id datasource_connection 表主键
     */
    void deleteDatasource(Integer id);

    /**
     * 测试连接：建立一次 JDBC 连接，读取厂商自报的库名与版本后关闭；不执行任何 SQL、不写库。
     *
     * <p>参数按「表单覆盖库中记录」合并：传 {@code id} 时以该条为底，表单里非空字段覆盖之，
     * 于是编辑场景可以在不回显密码的前提下复测；不传 {@code id} 时纯用表单试连，供保存前先验一次。</p>
     *
     * @param id    已保存的数据源 id，可为空
     * @param param 连接表单，可为空（仅在传了 id 时允许整体为空）
     * @return 数据库产品名与版本，如 {@code PostgreSQL 14.5}
     */
    String testConnection(Integer id, DatasourceConnectionParam param);

    /**
     * 上传 SQLite 数据库文件：落到本服务所在机器的 {@code /sqlite-data} 目录（文件名前拼 UUID 避免互相覆盖），
     * 落地后立即用 {@code jdbc:sqlite:} 真连一次，连不上或不是数据库文件则删除该文件并报错。
     *
     * <p>返回的 {@code jdbcUrl} 原样填进 {@code POST /datasource/connection}（{@code dbType=SQLITE}）即可保存；
     * 此时 {@code username} / {@code password} 都不用传，服务端按空串落库（驱动本来就忽略它们）。
     * 文件只在这台机器上，多实例部署时后续扫描请求必须打到同一节点。</p>
     *
     * @param file 上传的 .db / .sqlite / .sqlite3 文件
     * @return jdbcUrl、落盘路径、文件名、字节数与实测表数量
     */
    SqliteDatabaseUploadVO uploadSqliteDatabase(MultipartFile file);

    /**
     * 扫描指定数据源的表注释（通过 JDBC {@code DatabaseMetaData} 实时获取）。
     *
     * <p>连接参数完全取自数据源记录（{@code driverClass} / {@code jdbcUrl} / {@code dbName} /
     * {@code schemaName} / 账号密码），支持 POSTGRESQL / MYSQL / ORACLE / SQLSERVER 等主流关系型数据库；
     * 不同 {@code dbType} 下 {@code catalog} / {@code schema} 的填法在实现类内按厂商语义分派。</p>
     *
     * @param datasourceId datasource_connection 表主键
     * @return 表名 + 表注释列表
     */
    List<TableCommentVO> listTableComments(Integer datasourceId);

    /**
     * 扫描指定数据源下指定表的列信息（列名、列注释、数据类型、是否主键）。
     *
     * <p>实现上走 JDBC {@code DatabaseMetaData#getColumns} + {@code #getPrimaryKeys}，
     * catalog / schema 的分派规则与 {@link #listTableComments(Integer)} 一致。供 Agent
     * 在「功能三 对象属性推导」中取当前对象对应表的列清单。</p>
     *
     * @param datasourceId datasource_connection 表主键
     * @param tableName    表名（区分大小写，与 {@code DatabaseMetaData} 返回值保持一致）
     * @return 列信息列表，按 {@code ORDINAL_POSITION} 升序
     */
    List<TableColumnDescVO> listTableColumns(Integer datasourceId, String tableName);

    /**
     * 扫描指定对象空间对应数据源的表清单，附带「字段数」。供「数据源构建对象」向导的「选择表」步骤使用。
     *
     * <p>与 {@link #listTableComments(Integer)} 的区别只有字段数：后者是给大模型的最小返回体，
     * 向导页勾选表时需要知道每张表有多少列。全程一次 JDBC 连接，只读
     * {@code DatabaseMetaData}，不执行任何 SQL。</p>
     *
     * @param spaceId    归属的对象空间 id（每个空间至多一条数据源记录）
     * @param schemaName 外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）
     * @return 表名 + 表注释 + 字段数
     */
    List<DatasourceTableSummaryVO> listTablesWithColumnCount(Integer spaceId, String schemaName);

    /**
     * 一次扫描多张表的列信息与表间外键，供向导的「选择属性」步骤使用。
     *
     * <p>外键来源有两种：数据库声明的外键走 {@code DatabaseMetaData#getImportedKeys}，
     * 没有声明时按同名的 {@code *_id} 列推断并在 VO 里标 {@code HEURISTIC} 让前端提示用户确认。
     * 每张子表至多返回一条外键——实体表映射 {@code table_field_mapping} 的唯一约束就是
     * {@code (source_table_name, target_table_name)}，多条存不下。</p>
     *
     * @param spaceId    归属的对象空间 id（每个空间至多一条数据源记录）
     * @param schemaName 外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）
     * @param tableNames 表名集合（区分大小写）；只有集合内部的表之间才会给出外键
     * @return 每张表的列清单、主键列与指向其他勾选表的外键
     */
    List<DatasourceTableDetailVO> listTablesDetail(Integer spaceId, String schemaName, List<String> tableNames);

    /**
     * 扫描指定对象空间对应数据源下的 schema 清单。
     *
     * <p>按 dbType 分派：MySQL/MariaDB 走 {@link DatabaseMetaData#getCatalogs()}（数据库名即 schema），
     * 其余厂商走 {@link DatabaseMetaData#getSchemas()}。系统 schema（{@code pg_*}、{@code information_schema}、
     * Oracle 的 {@code SYS/SYSTEM}、SQL Server 的 {@code sys/db_*}、MySQL 的 {@code mysql/performance_schema/sys}）
     * 在结果里过滤掉，避免污染向导下拉。返回的 {@link DatasourceSchemaVO#getSchemaName()} 即后续
     * {@code /table/list}、{@code /table/columns}、{@code /build_object} 要回传的 schemaName 参数。</p>
     *
     * @param spaceId 归属的对象空间 id（每个空间至多一条数据源记录）
     * @return schema 清单
     */
    List<DatasourceSchemaVO> listSchemas(Integer spaceId);

    /**
     * 判断对象空间是否绑定了数据源（{@code datasource_connection} 里是否存在该 spaceId 的记录）。
     *
     * <p>供「数据源构建对象」决定要不要连外部库重扫列：绑定了就以现场扫描为准（类型、列注释、
     * 表与列是否存在都校验）；没绑定也允许建对象，只是这些外部信息一律拿不到，
     * 属性类型只能统一按 String 落。请求显式带了 {@code datasourceId} 时按那条数据源扫，不走本方法。
     * 只做存在性判断，不校验 JDBC URL、也不抛异常。</p>
     *
     * @param spaceId 归属的对象空间 id，可为空（空一律返回 false）
     * @return true 表示该空间绑定了数据源
     */
    boolean existsBySpace(Integer spaceId);

    /**
     * 按数据源 id 扫描 schema 清单，行为与 {@link #listSchemas(Integer)} 完全一致，
     * 只是归属键从 spaceId 换成 {@code datasource_connection} 主键。
     *
     * <p>供「还没有绑定对象空间」的数据源使用——{@code /datasource/connection/list} 只列未绑定空间的记录，
     * 那些记录按 spaceId 查不到，只能按 id 探。</p>
     *
     * @param datasourceId datasource_connection 表主键
     * @return schema 清单；id 不存在或 JDBC URL 为空时抛业务异常
     */
    List<DatasourceSchemaVO> listSchemasByDatasource(Integer datasourceId);

    /**
     * 按数据源 id 扫描表清单 + 字段数，行为与 {@link #listTablesWithColumnCount(Integer, String)} 一致。
     *
     * @param datasourceId datasource_connection 表主键
     * @param schemaName   外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）
     * @return 表名 + 表注释 + 字段数
     */
    List<DatasourceTableSummaryVO> listTablesWithColumnCountByDatasource(Integer datasourceId, String schemaName);

    /**
     * 按数据源 id 扫描多表列信息与表间外键，行为与 {@link #listTablesDetail(Integer, String, List)} 一致。
     *
     * @param datasourceId datasource_connection 表主键
     * @param schemaName   外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）
     * @param tableNames   表名集合（区分大小写）；只有集合内部的表之间才会给出外键
     * @return 每张表的列清单、主键列与指向其他勾选表的外键
     */
    List<DatasourceTableDetailVO> listTablesDetailByDatasource(Integer datasourceId, String schemaName,
                                                              List<String> tableNames);
}
