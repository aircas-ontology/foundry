package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.TableMetadataMapper;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TableMetadataServiceImpl implements TableMetadataService {

    @Resource
    private TableMetadataMapper tableMetadataMapper;

    @Override
    public List<TableColumnDescVO> getColumns(String datasourceId) {
        return tableMetadataMapper.getColumnMetadata(datasourceId).stream().map(v ->
                TableColumnDescVO.builder()
                        .columnName(v.getColumnName())
                        .description(v.getDescription())
                        .isPrimaryKey(v.getIsPrimaryKey())
                        .type(v.getType())
                        .build()
        ).collect(Collectors.toList());
    }

    @Override
    public List<DatasourceTableVO> listTables() {
        return tableMetadataMapper.listTables().stream().map(v -> DatasourceTableVO.builder().description(v.getDescription()).tableName(v.getTableName()).build())
                .collect(Collectors.toList());
    }

}
