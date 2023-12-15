package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.ontology.application.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyGroupBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyGroupVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyToGroupMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:04
 */

@Service
public class OntologyGroupServiceImpl implements OntologyGroupService {

    @Resource
    private OntologyGroupMapper ontologyGroupMapper;

    @Resource
    private OntologyToGroupMapper ontologyToGroupMapper;


    @Override
    public Integer add(OntologyGroupBO ontologyGroupBO) {
        return null;
    }

    @Override
    public Integer delete(Long id) {
        return null;
    }

    @Override
    public Integer update(OntologyGroupBO ontologyGroupBO) {
        return null;
    }

    @Override
    public OntologyGroupVO getOntologyGroupById(Long id) {
        return null;
    }
}
