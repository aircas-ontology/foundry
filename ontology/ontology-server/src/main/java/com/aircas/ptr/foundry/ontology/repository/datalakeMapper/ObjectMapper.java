package com.aircas.ptr.foundry.ontology.repository.datalakeMapper;

import com.aircas.ptr.foundry.ontology.model.po.DirectoryItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.google.common.collect.Lists;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Mapper
public interface ObjectMapper extends BaseMapper<Object> {

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

    List<Map<String, Object>> queryPrimaryKeyAndTitleKeyValue(@Param("tableName") String tableName,
                                                              @Param("primaryKeyColumnName") String primaryKeyColumnName,
                                                              @Param("titleKeyColumnName") String titleKeyColumnName);


    default List<Map<String, Object>> pageQuery(@Param("tableName") String tableName,
                                                @Param("columnNames") List<String> columnNames,
                                                @Param("filterColumnName") String filterColumnName,
                                                @Param("filterColumnValue") Object filterColumnValue,
                                                @Param("limit") Integer limit,
                                                @Param("offset") Integer offset,
                                                @Param("hasDeletedField") Boolean hasDeletedField) {
        var records = queryMapsPage(tableName, columnNames, filterColumnName, filterColumnValue, limit, offset, hasDeletedField);
        return populate(records, tableName, columnNames);
    }

    List<Map<String, Object>> queryMapsPage(@Param("tableName") String tableName,
                                            @Param("columnNames") List<String> columnNames,
                                            @Param("filterColumnName") String filterColumnName,
                                            @Param("filterColumnValue") Object filterColumnValue,
                                            @Param("limit") Integer limit,
                                            @Param("offset") Integer offset,
                                            @Param("hasDeletedField") Boolean hasDeletedField);


    List<String> queryColumnNames(@Param("tableName") String tableName);


    Integer queryCount(@Param("tableName") String tableName,
                       @Param("filterColumnName") String filterColumnName,
                       @Param("filterColumnValue") Object filterColumnValue,
                       @Param("hasDeletedField") Boolean hasDeletedField);


    default List<Map<String, Object>> populate(List<Map<String, Object>> records, String tableName, List<String> columns) {
        if (CollectionUtils.isEmpty(records)) {
            return records;
        }
        var columnNames = CollectionUtils.isEmpty(columns) ? queryColumnNames(tableName) : columns;
        if (records.size() == 1 && records.get(0) == null) {
            var map = new HashMap<String, Object>();
            columnNames.forEach(v -> map.put(v, null));
            return Lists.newArrayList(new TreeMap<>(map));
        }
        List<Map<String, Object>> newRecords = records.stream().map(r -> {
            columnNames.forEach(col -> {
                if (!r.containsKey(col)) {
                    r.put(col, null);
                }
            });
            return new TreeMap<>(r);
        }).collect(Collectors.toList());
        return newRecords;
    }

    List<Map<String, Object>> queryJoinTableData(@Param("primaryKeyValue") Object primaryKeyValue,
                                                 @Param("joinTableName") String joinTableName,
                                                 @Param("joinTableColumnNames") List<String> joinTableColumnNames,
                                                 @Param("joinTableKey") String joinTableKey,
                                                 @Param("joinTableOrderBy") String joinTableOrderBy,
                                                 @Param("count") Integer count);

    default List<Map<String, Object>> queryByJoinTable(@Param("primaryKeyValue") Object primaryKeyValue,
                                                       @Param("joinTableName") String joinTableName,
                                                       @Param("joinTableColumnNames") List<String> joinTableColumnNames,
                                                       @Param("joinTableKey") String joinTableKey,
                                                       @Param("joinTableOrderBy") String joinTableOrderBy,
                                                       @Param("count") Integer count) {
        var records = queryJoinTableData(primaryKeyValue, joinTableName, joinTableColumnNames, joinTableKey, joinTableOrderBy, count);
        return populate(records, joinTableName, joinTableColumnNames);
    }


    List<Map<String, Object>> queryByPrimaryKey(@Param("tableName") String tableName,
                                                @Param("columnNames") List<String> columnNames,
                                                @Param("primaryKeyColumn") String primaryKeyColumn,
                                                @Param("primaryKeyValue") Object primaryKeyValue);

    default List<Map<String, Object>> queryDataByPrimaryKey(@Param("tableName") String tableName,
                                                            @Param("columnNames") List<String> columnNames,
                                                            @Param("primaryKeyColumn") String primaryKeyColumn,
                                                            @Param("primaryKeyValue") Object primaryKeyValue) {
        var records = queryByPrimaryKey(tableName, columnNames, primaryKeyColumn, primaryKeyValue);
        return populate(records, tableName, columnNames);
    }


    void updateObject(String tableName, Map<String, Object> columnMap, String primaryKeyColumn, Object primaryKeyValue);


    void insertObject(String tableName, Map<String, Object> columnMap);


}