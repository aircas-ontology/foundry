package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.Function;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FunctionMapper extends tk.mybatis.mapper.common.Mapper<Function> {

    int insert(Function function);

    int updateByApi(Function function);

    Function selectByApi(String api);

    Integer deleteByApi(String api);

    List<Function> getAllFunctions();
}