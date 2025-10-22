package com.aircas.ptr.foundry.ontology.entity.repository.mapper.datalake;

import lombok.var;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper
public interface DataObjectMapper {

    //todo 后续改成分页查询，前期数据量小暂不考虑
    List<Map<String, Object>> queryTableDataByColumn(@Param("tableName") String tableName,
                                                     @Param("columnNames") Map<String, String> columnNames,
                                                     @Param("orderBy") String orderBy);

    default List<Map<String, Object>> queryTableDataByColumns(String tableName, Map<String, String> columnNames, String orderBy) {
        var rows = queryTableDataByColumn(tableName, columnNames, orderBy);
        var cols = columnNames.values().stream().collect(Collectors.toList());
        rows.forEach(row -> {
            cols.forEach(col -> {
                if (!row.containsKey(col)) {
                    row.put(col, null);
                }
            });
        });
        return rows;
    }


    String checkIdColumnExists(@Param("tableName") String tableName);

    String queryPrimaryKeyColumnName(@Param("tableName") String tableName);


}
