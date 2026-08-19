package com.aircas.ptr.foundry.ontology.repository.datalakeMapper;

import com.aircas.ptr.foundry.ontology.model.po.TableFieldMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TableFieldMappingMapper {

    TableFieldMapping selectBySourceAndTarget(@Param("schemaName") String schemaName,
                                              @Param("sourceTableName") String sourceTableName,
                                              @Param("targetTableName") String targetTableName);

    List<TableFieldMapping> selectBySourceTable(@Param("schemaName") String schemaName,
                                                @Param("sourceTableName") String sourceTableName);

    TableFieldMapping selectByTargetTable(@Param("schemaName") String schemaName,
                                          @Param("targetTableName") String targetTableName);

    List<TableFieldMapping> selectBySourceTableAndColumn(@Param("schemaName") String schemaName,
                                                         @Param("sourceTableName") String sourceTableName,
                                                         @Param("sourceColumnName") String sourceColumnName);

    int insertMapping(@Param("schemaName") String schemaName,
                      @Param("mapping") TableFieldMapping mapping);
}
