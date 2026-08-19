package com.aircas.ptr.foundry.ontology.repository.datalakeMapper;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceTable;
import com.aircas.ptr.foundry.ontology.model.po.TableColumnDesc;
import com.aircas.ptr.foundry.ontology.model.po.TableDesc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TableMetadataMapper {

    List<TableColumnDesc> queryColumnMetadata(@Param("schemaName") String schemaName, @Param("tableName") String tableName);

    List<DatasourceTable> listTables(@Param("schemaName") String schemaName);

    List<DatasourceTable> listTablesPage(@Param("schemaName") String schemaName,
                                         @Param("keyword") String keyword,
                                         @Param("limit") Integer limit,
                                         @Param("offset") Integer offset);

    Integer countTables(@Param("schemaName") String schemaName, @Param("keyword") String keyword);

    Boolean isColumnExist(@Param("schemaName") String schemaName,@Param("tableName") String tableName, @Param("columnName") String columnName);


    Boolean isTableExist(@Param("schemaName") String schemaName, @Param("tableName") String tableName);


    String queryPrimaryKeyColumnName(@Param("schemaName") String schemaName, @Param("tableName") String tableName);


    void createTable(@Param("schemaName") String schemaName,@Param("table") TableDesc table, @Param("columns") List<TableColumnDesc> columns);

    void createIndex(@Param("schemaName") String schemaName, @Param("tableName") String tableName, @Param("columnName") String columnName);

    void addColumns(@Param("schemaName") String schemaName, @Param("tableName") String tableName, @Param("columns") List<TableColumnDesc> columns);

    void dropColumns(@Param("schemaName") String schemaName, @Param("tableName") String tableName, @Param("columns") List<String> columns);

    void dropTable(@Param("schemaName") String schemaName, @Param("tableName") String tableName);

    void createSchema(@Param("schemaName") String schemaName);

    void initSpaceSchema(@Param("schemaName") String schemaName);

}