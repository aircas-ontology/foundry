package com.aircas.ptr.foundry.ontology.entity.repository.mapper.main;

import com.aircas.ptr.foundry.ontology.entity.model.po.EntityPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EntityMapper extends BaseMapper<EntityPO> {
}
