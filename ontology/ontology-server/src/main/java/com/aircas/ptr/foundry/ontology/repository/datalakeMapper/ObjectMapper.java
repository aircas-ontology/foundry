package com.aircas.ptr.foundry.ontology.repository.datalakeMapper;

import com.aircas.ptr.foundry.ontology.model.po.DirectoryItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ObjectMapper extends BaseMapper<Object> {

    List<DirectoryItem> queryDirectory(String tableName, String primaryKey, String titleKey);
    List<Map<String, Object>> queryAnySQL(String sql);

    int updateAnySQL(String updateSql);

}