package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.DatasourceConnectionParam;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionItemVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableCommentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

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
     * {@code password} 此处必填，{@code status} 不传按 1（启用）落库，{@code dbType} 统一转大写存储
     * （扫表时按 dbType 分派 catalog / schema，小写值会落到未知厂商分支）。</p>
     *
     * @param param 连接配置入参
     * @return 落库后的完整配置（不含密码）
     */
    DatasourceConnectionItemVO createDatasource(DatasourceConnectionParam param);

    /**
     * 修改数据源连接配置。
     *
     * <p>除密码外，其余字段一律按入参覆盖——入参里给空值就是清空该列（如描述、schema）。
     * {@code password} 留空表示沿用库中原密码：密码任何查询接口都不返回，若强制必填就无法保存编辑。</p>
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
     * 在「功能三 对象属性推导」中取当前本体对象对应表的列清单。</p>
     *
     * @param datasourceId datasource_connection 表主键
     * @param tableName    表名（区分大小写，与 {@code DatabaseMetaData} 返回值保持一致）
     * @return 列信息列表，按 {@code ORDINAL_POSITION} 升序
     */
    List<TableColumnDescVO> listTableColumns(Integer datasourceId, String tableName);
}
