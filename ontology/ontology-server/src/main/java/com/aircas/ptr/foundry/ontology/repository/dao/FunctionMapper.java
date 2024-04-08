package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.Function;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FunctionMapper {

    int insert(Function function);


    int insertSelective(Function function);

    Function selectByApi(String api);

    List<Function> getAllFunctions();
}