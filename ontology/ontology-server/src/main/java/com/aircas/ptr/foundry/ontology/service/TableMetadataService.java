package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;

import java.util.List;



public interface TableMetadataService {

    List<TableColumnDescVO> getColumns(String datasourceId);

    List<DatasourceTableVO> listTables();

    void dropDataSource(String dataSourceId);

    void dropColumns(String dataSourceId, List<String> columns);
}
