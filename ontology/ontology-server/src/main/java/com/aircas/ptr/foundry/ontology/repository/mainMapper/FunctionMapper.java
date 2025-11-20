package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.view.FunctionView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FunctionMapper extends BaseMapper<Function> {

    int insert(Function function);

    int updateByApi(Function function);

    Function selectByApi(String api);

    Integer deleteByApi(String api);

    List<Function> getAllFunctions();

    List<Function> selectByPage(@Param("limit") Integer limit, @Param("offset") Integer offset);

    List<FunctionView> selectFunctionViewsByOntologyId(String ontologyUniqId);
}