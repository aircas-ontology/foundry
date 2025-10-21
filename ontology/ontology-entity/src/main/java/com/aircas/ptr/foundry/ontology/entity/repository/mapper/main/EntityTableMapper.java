package com.aircas.ptr.foundry.ontology.entity.repository.mapper.main;

import com.aircas.ptr.foundry.ontology.entity.model.dto.TableCreateDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.var;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface EntityTableMapper extends BaseMapper<Object> {

    void createTable(@Param("tableDto") TableCreateDTO tableDto);

    void batchInsertRows(@Param("tableName") String tableName, @Param("rows") List<Map<String, Object>> rows);

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
        populate(records, tableName);
        return records;
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
        populate(records, joinTableName);
        return records;
    }


    List<Map<String, Object>> selectByPrimaryKey(@Param("tableName") String tableName,
                                                 @Param("primaryKeyColumn") String primaryKeyColumn,
                                                 @Param("primaryKeyValue") Object primaryKeyValue);

    default List<Map<String, Object>> selectDataByPrimaryKey(@Param("tableName") String tableName,
                                                             @Param("primaryKeyColumn") String primaryKeyColumn,
                                                             @Param("primaryKeyValue") Object primaryKeyValue) {
        var records = selectByPrimaryKey(tableName, primaryKeyColumn, primaryKeyValue);
        populate(records, tableName);
        return records;
    }

    default void populate(List<Map<String, Object>> records, String tableName) {
        var columnNames = getColumnNames(tableName);
        records.forEach(r -> {
            columnNames.forEach(col -> {
                if (!r.containsKey(col)) {
                    r.put(col, null);
                }
            });
        });
    }

    String checkIdColumnExists(@Param("tableName") String tableName);


}
