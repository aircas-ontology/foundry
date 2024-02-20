package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.TableColumnDesc;
import com.aircas.ptr.foundry.ontology.application.service.TableMetadataService;
import com.aircas.ptr.foundry.ontology.entity.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.repository.datalakeDao.TableMetadataMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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
        List<TableColumnDesc> list = this.tableMetadataMapper.getColumnMetadata(datasourceId);
        return list.stream().map(item -> {
           TableColumnDescVO tableColumnDescVO = new TableColumnDescVO();
           BeanUtils.copyProperties(item, tableColumnDescVO);
           return tableColumnDescVO;
        }).collect(Collectors.toList());
    }

}
