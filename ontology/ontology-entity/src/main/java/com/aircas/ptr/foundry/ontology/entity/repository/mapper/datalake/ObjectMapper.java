package com.aircas.ptr.foundry.ontology.entity.repository.mapper.datalake;

import com.aircas.ptr.foundry.ontology.entity.model.po.DirectoryItemPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ObjectMapper {

    List<DirectoryItemPO> queryDirectory(String tableName, String primaryKey, String titleKey);

    List<Map<String, Object>> queryAnySQL(String sql);

    int updateAnySQL(String updateSql);

}