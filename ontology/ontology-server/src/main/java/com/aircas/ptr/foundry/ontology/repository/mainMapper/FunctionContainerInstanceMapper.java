package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 容器算子运行实例 Mapper（单表，条件用 Wrapper，无需 XML）。
 */
@Mapper
public interface FunctionContainerInstanceMapper extends BaseMapper<FunctionContainerInstance> {
}
