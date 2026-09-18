package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologyLinkGroupConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyLinkGroupDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyLinkGroupRepository;
import com.aircas.ptr.foundry.ontology.service.LinkGroupCdcJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class LinkGroupCdcJobServiceImpl implements LinkGroupCdcJobService {
    @Autowired
    OntologyLinkGroupRepository ontologyLinkGroupRepository;
    @Override
    public void handleCreateCdc(OntologyLinkGroup after) {
        EsOntologyLinkGroupDTO esOntologyLinkGroupDTO = OntologyLinkGroupConverter.convert(after);
        ontologyLinkGroupRepository.save(esOntologyLinkGroupDTO);
    }

    @Override
    public boolean handleUpdateCdc(OntologyLinkGroup before, OntologyLinkGroup after) {
        return false;
    }

    @Override
    public boolean handleDeleteCdc(OntologyLinkGroup before) {
        return false;
    }
}
