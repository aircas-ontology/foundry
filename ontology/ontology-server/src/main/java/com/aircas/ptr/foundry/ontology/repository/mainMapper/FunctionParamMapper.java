package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FunctionParamMapper extends BaseMapper<FunctionParamPO> {

    int insertBatch(@Param("params") List<FunctionParamPO> params);

    int deleteByFunctionId(Long functionId);

    List<FunctionParamPO> selectByFunctionId(Long functionId);

}
