package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;


public interface TableMetadataService {

    List<TableColumnDescVO> getColumns(Integer spaceId, String datasourceId);

    void dropDataSource(String schemaName, String dataSourceId);

    void dropColumns(String schemaName, String dataSourceId, List<String> columns);

    void createSchema(String schemaName);

    void initSpaceSchema(String schemaName);

    Page<DatasourceTableVO> getTables(Integer spaceId, String keyword, Integer pageNum, Integer pageSize);
}
