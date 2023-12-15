package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.ontology.application.service.OntologyLinkService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyLinkMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:03
 */

@Service
public class OntologyLinkServiceImpl implements OntologyLinkService {

    @Resource
    private OntologyLinkMapper ontologyLinkMapper;


    @Override
    public Integer add(OntologyLinkBO ontologyLinkBO) {
        return null;
    }

    @Override
    public Integer delete(Long id) {
        return null;
    }

    @Override
    public Integer update(OntologyLinkBO ontologyLinkBO) {
        return null;
    }

    @Override
    public OntologyLinkVO getOntologyLinkById(Long id) {
        return null;
    }
}
