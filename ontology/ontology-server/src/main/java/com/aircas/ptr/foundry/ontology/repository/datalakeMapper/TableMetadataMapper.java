package com.aircas.ptr.foundry.ontology.repository.datalakeMapper;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceTable;
import com.aircas.ptr.foundry.ontology.model.po.TableColumnDesc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TableMetadataMapper {

    List<TableColumnDesc> queryColumnMetadata(@Param("tableName") String tableName);

    List<DatasourceTable> listTables();

    Boolean isColumnExist(@Param("tableName") String tableName, @Param("columnName") String columnName);

    String queryPrimaryKeyColumnName(@Param("tableName") String tableName);

}