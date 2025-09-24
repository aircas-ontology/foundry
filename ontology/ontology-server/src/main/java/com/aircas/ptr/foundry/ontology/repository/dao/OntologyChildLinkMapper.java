package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.OntologyChildLink;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OntologyChildLinkMapper extends BaseMapper<OntologyChildLink> {

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int deleteByPrimaryKey(Long id);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insert(OntologyChildLink record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insertSelective(OntologyChildLink record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    OntologyChildLink selectByPrimaryKey(Long id);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int updateByPrimaryKeySelective(OntologyChildLink record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int updateByPrimaryKey(OntologyChildLink record);
}