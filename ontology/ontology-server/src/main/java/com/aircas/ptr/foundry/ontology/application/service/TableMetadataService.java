package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.entity.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.entity.vo.DatasourceTableVO;
import com.github.pagehelper.PageInfo;

import java.util.List;



public interface TableMetadataService {

    List<TableColumnDescVO> getColumns(String datasourceId);

    List<DatasourceTableVO> listTables();
}
