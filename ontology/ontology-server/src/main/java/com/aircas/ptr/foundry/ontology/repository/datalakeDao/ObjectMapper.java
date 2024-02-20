package com.aircas.ptr.foundry.ontology.repository.datalakeDao;

import com.aircas.ptr.foundry.model.po.DirectoryItem;
import com.aircas.ptr.foundry.model.po.TableColumnDesc;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ObjectMapper {

    List<DirectoryItem> queryDirectory(String tableName, String primaryKey, String titleKey);

}