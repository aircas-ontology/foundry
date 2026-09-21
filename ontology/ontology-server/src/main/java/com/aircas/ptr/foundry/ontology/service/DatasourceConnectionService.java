package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableCommentVO;
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
