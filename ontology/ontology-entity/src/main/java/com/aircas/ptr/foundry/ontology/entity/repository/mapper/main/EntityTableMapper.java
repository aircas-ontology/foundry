package com.aircas.ptr.foundry.ontology.entity.repository.mapper.main;

import com.aircas.ptr.foundry.ontology.entity.model.dto.TableCreateDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface EntityTableMapper extends BaseMapper<Object> {

    void createTable(@Param("tableDto") TableCreateDTO tableDto);

    void batchInsertRows(@Param("tableName") String tableName, @Param("rows") List<Map<String, Object>> rows);

    void copyTable(@Param("srcTable") String srcTable, @Param("newTable") String newTable);

}
