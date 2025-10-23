package com.aircas.ptr.foundry.ontology.entity.repository.mapper.main;

import com.aircas.ptr.foundry.ontology.entity.model.dto.FieldDTO;
import com.aircas.ptr.foundry.ontology.entity.model.dto.TableCreateDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.google.common.collect.Lists;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface EntityTableMapper extends BaseMapper<Object> {

    void updateByPrimaryKey(@Param("tableName") String tableName,
                            @Param("primaryKey") String primaryKey,
                            @Param("rows") List<Map<String, Object>> rows);

    void createColumns(@Param("fields") List<FieldDTO> fields);

    void createTable(@Param("tableDto") TableCreateDTO tableDto);

    void insertRows(@Param("tableName") String tableName, @Param("rows") List<Map<String, Object>> rows);

    default void batchInsertRows(@Param("tableName") String tableName, @Param("rows") List<Map<String, Object>> rows) {
        var partition = Lists.partition(rows, 1000);
        partition.forEach(p -> insertRows(tableName, p));
    }

    void copyTable(@Param("srcTable") String srcTable, @Param("newTable") String newTable);

    void dropTable(@Param("tableName") String tableName);

    List<String> getColumnNames(@Param("tableName") String tableName);


    List<Map<String, Object>> selectMapsPage(@Param("tableName") String tableName,
                                             @Param("limit") Integer limit,
                                             @Param("offset") Integer offset);


    default List<Map<String, Object>> pageSelect(@Param("tableName") String tableName,
                                                 @Param("limit") Integer limit,
                                                 @Param("offset") Integer offset) {
        var records = selectMapsPage(tableName, limit, offset);
        return populate(records, tableName);
    }

    Integer selectCount(@Param("tableName") String tableName);


    String queryPrimaryKeyColumnName(@Param("tableName") String tableName);


    List<Map<String, Object>> selectJoinTableData(@Param("tableName") String tableName,
                                                  @Param("tableKey") String tableKey,
                                                  @Param("primaryKeyColumn") String primaryKeyColumn,
                                                  @Param("primaryKeyValue") Object primaryKeyValue,
                                                  @Param("joinTableName") String joinTableName,
                                                  @Param("joinTableKey") String joinTableKey,
                                                  @Param("joinTableOrderBy") String joinTableOrderBy,
                                                  @Param("count") Integer count);

    default List<Map<String, Object>> selectByJoinTable(@Param("tableName") String tableName,
                                                        @Param("tableKey") String tableKey,
                                                        @Param("primaryKeyColumn") String primaryKeyColumn,
                                                        @Param("primaryKeyValue") Object primaryKeyValue,
                                                        @Param("joinTableName") String joinTableName,
                                                        @Param("joinTableKey") String joinTableKey,
                                                        @Param("joinTableOrderBy") String joinTableOrderBy,
                                                        @Param("count") Integer count) {
        var records = selectJoinTableData(tableName, tableKey, primaryKeyColumn, primaryKeyValue, joinTableName, joinTableKey, joinTableOrderBy, count);
        return populate(records, joinTableName);
    }


    List<Map<String, Object>> selectByPrimaryKey(@Param("tableName") String tableName,
                                                 @Param("primaryKeyColumn") String primaryKeyColumn,
                                                 @Param("primaryKeyValue") Object primaryKeyValue);

    default List<Map<String, Object>> selectDataByPrimaryKey(@Param("tableName") String tableName,
                                                             @Param("primaryKeyColumn") String primaryKeyColumn,
                                                             @Param("primaryKeyValue") Object primaryKeyValue) {
        var records = selectByPrimaryKey(tableName, primaryKeyColumn, primaryKeyValue);
        return populate(records, tableName);
    }

    default List<Map<String, Object>> populate(List<Map<String, Object>> records, String tableName) {
        var columnNames = getColumnNames(tableName);
        if (CollectionUtils.isEmpty(records) || (records.size() == 1 && records.get(0) == null)) {
            var map = new HashMap<String, Object>();
            columnNames.forEach(v -> map.put(v, null));
            return Lists.newArrayList(map);
        }
        records.forEach(r -> {
            columnNames.forEach(col -> {
                if (!r.containsKey(col)) {
                    r.put(col, null);
                }
            });
        });
        return records;
    }

    String checkIdColumnExists(@Param("tableName") String tableName);


}
