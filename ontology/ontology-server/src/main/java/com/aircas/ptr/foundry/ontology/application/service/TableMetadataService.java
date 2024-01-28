package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.entity.vo.TableColumnDescVO;

import java.util.List;



public interface TableMetadataService {

    List<TableColumnDescVO> getColumns(String datasourceId);

}
