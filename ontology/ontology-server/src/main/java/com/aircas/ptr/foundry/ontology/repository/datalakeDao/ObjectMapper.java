package com.aircas.ptr.foundry.ontology.repository.datalakeDao;

import com.aircas.ptr.foundry.model.po.DirectoryItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ObjectMapper {

    List<DirectoryItem> queryDirectory(String tableName, String primaryKey, String titleKey);
    List<Map<String, Object>> queryAnySQL(String sql);

    int updateAnySQL(String updateSql);

}