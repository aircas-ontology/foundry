package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.Catalog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CatalogMapper extends tk.mybatis.mapper.common.Mapper<Catalog> {

    @Select("select * from catalog where parent_id = #{parentId} order by id asc")
    List<Catalog> queryByParentId(@Param("parentId") Long parentId);

    @Select("select count(*) from catalog where parent_id = #{parentId}")
    Long queryCountByParentId(@Param("parentId") Long parentId);

    @Select("select count(*) from catalog where status = #{status}")
    Long queryCountByStatus(@Param("status") Integer status);

}
