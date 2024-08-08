package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyGroupBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkCountVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyGroupVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.repository.param.OntologyGroupAddParam;
import com.github.pagehelper.PageInfo;

import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:02
 */


public interface OntologyGroupService {

    Integer add(OntologyGroupAddParam param);

    Integer delete(List<Long> ids);

    Integer update(OntologyGroupBO ontologyGroupBO);

    OntologyGroupVO getOntologyGroupById(Long id);

    PageInfo<OntologyGroupVO> list(Integer page, Integer size);

    OntologyLinkGraphVO getOntologyGroupLinks(String id);

    PageInfo<OntologyGroupVO> search(String keyword, Integer page, Integer size);
}
