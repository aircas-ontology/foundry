package com.aircas.ptr.foundry.ontology.repository.datalakeDao;

import com.aircas.ptr.foundry.model.po.DatasourceTable;
import com.aircas.ptr.foundry.model.po.TableColumnDesc;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TableMetadataMapper {

    List<TableColumnDesc> getColumnMetadata(String tableName);

    List<DatasourceTable> listTables();
}