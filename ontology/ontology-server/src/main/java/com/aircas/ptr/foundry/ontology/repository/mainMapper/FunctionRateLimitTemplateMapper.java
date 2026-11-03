package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.FunctionRateLimitTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 限流配置模板 Mapper（单表，条件用 Wrapper，无需 XML）。
 */
@Mapper
public interface FunctionRateLimitTemplateMapper extends BaseMapper<FunctionRateLimitTemplate> {
}
