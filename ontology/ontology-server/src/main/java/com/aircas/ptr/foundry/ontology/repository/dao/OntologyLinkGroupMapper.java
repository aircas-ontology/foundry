package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface OntologyLinkGroupMapper extends BaseMapper<OntologyLinkGroup> {
    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int deleteByPrimaryKey(Long id);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int deleteByUniqueIdentifier(String uniqueIdentifier);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insert(OntologyLinkGroup record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int insertSelective(OntologyLinkGroup record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    OntologyLinkGroup selectByPrimaryKey(Long id);

    OntologyLinkGroup selectByUniqueIdentifier(String uniqueIdentifier);

    List<OntologyLinkGroup> selectAll();


    /**
     * @mbg.generated generated automatically, do not modify!
     */
    List<OntologyLinkGroup> selectByOntologyUniqueIdentifierFrom(String uniqueIdentifier);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    List<OntologyLinkGroup> selectByOntologyUniqueIdentifierTo(String uniqueIdentifier);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int updateByPrimaryKeySelective(OntologyLinkGroup record);

    /**
     * @mbg.generated generated automatically, do not modify!
     */
    int updateByPrimaryKey(OntologyLinkGroup record);

    List<OntologyLinkGroup> getLinkByOntologies(List<String> ontologyIds);

    Integer countByStatus(int status);
}