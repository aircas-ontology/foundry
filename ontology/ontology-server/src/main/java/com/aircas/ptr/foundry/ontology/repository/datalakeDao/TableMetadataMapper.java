package com.aircas.ptr.foundry.ontology.repository.datalakeDao;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceTable;
import com.aircas.ptr.foundry.ontology.model.po.TableColumnDesc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TableMetadataMapper {

    List<TableColumnDesc> getColumnMetadata(@Param("tableName") String tableName);

    List<DatasourceTable> listTables();
}