package com.aircas.ptr.foundry.ontology.repository.datalakeMapper;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceTable;
import com.aircas.ptr.foundry.ontology.model.po.TableColumnDesc;
import com.aircas.ptr.foundry.ontology.model.po.TableDesc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TableMetadataMapper {

    List<TableColumnDesc> queryColumnMetadata(@Param("tableName") String tableName);

    List<DatasourceTable> listTables();

    Boolean isColumnExist(@Param("tableName") String tableName, @Param("columnName") String columnName);


    Boolean isTableExist(@Param("tableName") String tableName);


    String queryPrimaryKeyColumnName(@Param("tableName") String tableName);


    void createTable(@Param("table") TableDesc table, @Param("columns") List<TableColumnDesc> columns);


    void addColumns(@Param("tableName") String tableName, @Param("columns") List<TableColumnDesc> columns);


    void dropColumns(@Param("tableName") String tableName, @Param("columns") List<String> columns);

    void dropTable(@Param("tableName") String tableName);


}