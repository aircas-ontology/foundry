package com.aircas.ptr.foundry.ontology.entity.repository.mapper.datalake;

import com.aircas.ptr.foundry.ontology.entity.model.po.DirectoryItemPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DataObjectMapper {

    List<DirectoryItemPO> queryDirectory(String tableName, String primaryKey, String titleKey);

    List<Map<String, Object>> queryAnySQL(String sql);

    //todo 后续改成分页查询，前期数据量小暂不考虑
    List<Map<String, Object>> queryTableData(@Param("tableName") String tableName);

    //todo 后续改成分页查询，前期数据量小暂不考虑
    List<Map<String, Object>> queryTableDataByColumn(@Param("tableName") String tableName,
                                                     @Param("columnNames") Map<String, String> columnNames,
                                                     @Param("orderBy") String orderBy);

    int updateAnySQL(String updateSql);


    String checkIdColumnExists(@Param("tableName") String tableName );

}
