package com.aircas.ptr.foundry.ontology.repository.datalakeMapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.google.common.collect.Lists;
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

    List<Map<String, Object>> queryTableDataByColumn(@Param("schemaName") String schemaName,
                                                     @Param("tableName") String tableName,
                                                     @Param("columnNames") Map<String, String> columnNames,
                                                     @Param("orderBy") String orderBy);

    default List<Map<String, Object>> queryTableDataByColumns(String schemaName, String tableName, Map<String, String> columnNames, String orderBy) {
        var rows = queryTableDataByColumn(schemaName, tableName, columnNames, orderBy);
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

    List<Object> queryPrimaryKeyValue(@Param("schemaName") String schemaName,
                                      @Param("tableName") String tableName,
                                      @Param("primaryKeyColumnName") String primaryKeyColumnName);

    List<Map<String, Object>> queryPrimaryKeyAndTitleKeyValue(@Param("schemaName") String schemaName,
                                                              @Param("tableName") String tableName,
                                                              @Param("primaryKeyColumnName") String primaryKeyColumnName,
                                                              @Param("titleKeyColumnName") String titleKeyColumnName);


    default List<Map<String, Object>> pageQuery(@Param("schemaName") String schemaName,
                                                @Param("tableName") String tableName,
                                                @Param("columnNames") List<String> columnNames,
                                                @Param("filterColumnName") String filterColumnName,
                                                @Param("filterColumnValue") Object filterColumnValue,
                                                @Param("limit") Integer limit,
                                                @Param("offset") Integer offset,
                                                @Param("hasDeletedField") Boolean hasDeletedField) {
        var records = queryMapsPage(schemaName, tableName, columnNames, filterColumnName, filterColumnValue, limit, offset, hasDeletedField);
        return populate(schemaName, records, tableName, columnNames);
    }

    List<Map<String, Object>> queryMapsPage(@Param("schemaName") String schemaName,
                                            @Param("tableName") String tableName,
                                            @Param("columnNames") List<String> columnNames,
                                            @Param("filterColumnName") String filterColumnName,
                                            @Param("filterColumnValue") Object filterColumnValue,
                                            @Param("limit") Integer limit,
                                            @Param("offset") Integer offset,
                                            @Param("hasDeletedField") Boolean hasDeletedField);


    List<String> queryColumnNames(@Param("schemaName") String schemaName, @Param("tableName") String tableName);


    Integer queryCount(@Param("schemaName") String schemaName,
                       @Param("tableName") String tableName,
                       @Param("filterColumnName") String filterColumnName,
                       @Param("filterColumnValue") Object filterColumnValue,
                       @Param("hasDeletedField") Boolean hasDeletedField);


    default List<Map<String, Object>> populate(String schemaName, List<Map<String, Object>> records, String tableName, List<String> columns) {
        if (CollectionUtils.isEmpty(records)) {
            return records;
        }
        var columnNames = CollectionUtils.isEmpty(columns) ? queryColumnNames(schemaName, tableName) : columns;
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

    List<Map<String, Object>> queryJoinTableData(@Param("schemaName") String schemaName,
                                                 @Param("primaryKeyValue") Object primaryKeyValue,
                                                 @Param("joinTableName") String joinTableName,
                                                 @Param("joinTableColumnNames") List<String> joinTableColumnNames,
                                                 @Param("joinTableKey") String joinTableKey,
                                                 @Param("joinTableOrderBy") String joinTableOrderBy,
                                                 @Param("count") Integer count,
                                                 @Param("sort") String sort);

    default List<Map<String, Object>> queryByJoinTable(@Param("schemaName") String schemaName,
                                                       @Param("primaryKeyValue") Object primaryKeyValue,
                                                       @Param("joinTableName") String joinTableName,
                                                       @Param("joinTableColumnNames") List<String> joinTableColumnNames,
                                                       @Param("joinTableKey") String joinTableKey,
                                                       @Param("joinTableOrderBy") String joinTableOrderBy,
                                                       @Param("count") Integer count,
                                                       @Param("sort") String sort) {
        var records = queryJoinTableData(schemaName, primaryKeyValue, joinTableName, joinTableColumnNames, joinTableKey, joinTableOrderBy, count, sort);
        return populate(schemaName, records, joinTableName, joinTableColumnNames);
    }


    Map<String, Object> queryByPrimaryKey(@Param("schemaName") String schemaName,
                                          @Param("tableName") String tableName,
                                          @Param("columnNames") List<String> columnNames,
                                          @Param("primaryKeyColumn") String primaryKeyColumn,
                                          @Param("primaryKeyValue") Object primaryKeyValue);

    default Map<String, Object> queryDataByPrimaryKey(@Param("schemaName") String schemaName,
                                                      @Param("tableName") String tableName,
                                                      @Param("columnNames") List<String> columnNames,
                                                      @Param("primaryKeyColumn") String primaryKeyColumn,
                                                      @Param("primaryKeyValue") Object primaryKeyValue) {
        var records = queryByPrimaryKey(schemaName, tableName, columnNames, primaryKeyColumn, primaryKeyValue);
        return populate(schemaName, Lists.newArrayList(records), tableName, columnNames).get(0);
    }


    default List<Map<String, Object>> queryDataByPrimaryKeyList(@Param("schemaName") String schemaName,
                                                                @Param("tableName") String tableName,
                                                                @Param("columnNames") List<String> columnNames,
                                                                @Param("primaryKeyColumn") String primaryKeyColumn,
                                                                @Param("primaryKeyValueList") List<Object> primaryKeyValueList) {
        var records = queryByPrimaryKeyList(schemaName, tableName, columnNames, primaryKeyColumn, primaryKeyValueList);
        return populate(schemaName, records, tableName, columnNames);
    }

    List<Map<String, Object>> queryByPrimaryKeyList(@Param("schemaName") String schemaName,
                                                    @Param("tableName") String tableName,
                                                    @Param("columnNames") List<String> columnNames,
                                                    @Param("primaryKeyColumn") String primaryKeyColumn,
                                                    @Param("primaryKeyValueList") List<Object> primaryKeyValueList);


    void updateObject(String schemaName, String tableName, Map<String, Object> columnMap, String primaryKeyColumn, Object primaryKeyValue);


    void insertObject(String schemaName, String tableName, Map<String, Object> columnMap);

    List<Object> batchInsertObjectReturnKey(@Param("schemaName") String schemaName,
                                            @Param("tableName") String tableName,
                                            @Param("columnValues") List<Map<String, Object>> columnValues,
                                            @Param("primaryKey") String primaryKey);


    void batchInsertObject(@Param("schemaName") String schemaName,
                           @Param("tableName") String tableName,
                           @Param("columnValues") List<Map<String, Object>> columnValues);

    void deleteByTableName(@Param("schemaName") String schemaName, @Param("tableName") String tableName);


    void deleteByTableNameAndColumn(@Param("schemaName") String schemaName,
                                    @Param("tableName") String tableName,
                                    @Param("columnName") String columnName,
                                    @Param("columnValue") Object columnValue);

    void deleteByTableNameAndColumns(@Param("schemaName") String schemaName,
                                     @Param("tableName") String tableName,
                                     @Param("columnName") String columnName,
                                     @Param("columnValues") List<Object> columnValues);


    List<Map<String, Object>> queryBySql(@Param("sql") String sql);

    Integer queryCountBySql(@Param("sql") String sql);

    Integer count(@Param("schemaName") String schemaName,
                  @Param("tableName") String tableName);
}