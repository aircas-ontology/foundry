package com.aircas.ptr.foundry.ontology.repository.datalakeMapper;

import com.aircas.ptr.foundry.ontology.model.po.DirectoryItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.var;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper
public interface ObjectMapper extends BaseMapper<Object> {

    List<DirectoryItem> queryDirectory(String tableName, String primaryKey, String titleKey);

    List<Map<String, Object>> queryAnySQL(String sql);

    int updateAnySQL(String updateSql);


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

    List<Object> queryPrimaryKeyValue(@Param("tableName") String tableName,
                                      @Param("primaryKeyColumnName") String primaryKeyColumnName);

}