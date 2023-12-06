package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.model.po.Catalog;
import com.aircas.ptr.foundry.model.po.Ontology;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OntologyMapper extends tk.mybatis.mapper.common.Mapper<Ontology> {

    @Select("select * from ontology order by id desc")
    List<Ontology> queryAll();

    @Select("select count(*) from ontology where status = #{status}")
    Long queryCountByStatus(@Param("status") Integer status);

    @Select("select count(*) from ontology where domain = #{domain} and status = #{status}")
    Long queryCountByDomain(@Param("domain") String domain, @Param("status") Integer status);

    @Select("select * from ontology where domain = #{domain} and name = #{name}")
    List<Ontology> queryOntologyByDomain(String domain, String name);
}
