package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.ColumnMetaDTO;
import com.aircas.ptr.foundry.ontology.model.dto.ForeignKeyDTO;
import com.aircas.ptr.foundry.ontology.model.dto.TableMetaDTO;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;

import java.util.List;

/**
 * 动态数据源元数据查询服务
 * <p>
 * 根据 datasource_connection 表中的记录，建立临时 JDBC 连接并读取元数据。
 * 当前仅实现 PostgreSQL；后续如需支持 MySQL/Oracle，可在 impl 里按 dbType 分派。
 */
public interface DatasourceMetadataService {

    /**
     * 查询数据源中所有用户表（表名 + 中文注释）
     *
     * @param conn       数据源连接配置
     * @param schemaName schema 名，为 null/空则使用配置里的 schemaName，仍为空则用 "public"
     */
    List<TableMetaDTO> listTables(DatasourceConnection conn, String schemaName);

    /**
     * 查询指定表的字段列表（字段名 + 类型 + 中文注释 + 主键标记）
     */
    List<ColumnMetaDTO> listColumns(DatasourceConnection conn, String schemaName, String tableName);

    /**
     * 查询指定 schema 下的所有外键关系
     */
    List<ForeignKeyDTO> listForeignKeys(DatasourceConnection conn, String schemaName);

    /**
     * 测试数据源连通性，返回 true 表示可正常建立连接
     */
    boolean testConnection(DatasourceConnection conn);
}
