package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.ActionCategory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行为分类体系树 Mapper。
 * <p>
 * 全部查询通过 MyBatis-Plus 的 LambdaQueryWrapper 表达，无需自定义 XML，
 * 与 OntologyCategoryMapper / OntologyLinkCategoryMapper 的既有写法保持一致。
 */
@Mapper
public interface ActionCategoryMapper extends BaseMapper<ActionCategory> {
}
