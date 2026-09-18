package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologySpaceConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologySpaceDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologySpaceRepository;
import com.aircas.ptr.foundry.ontology.service.SpaceCdcJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class SpaceCdcJobServiceImpl implements SpaceCdcJobService {
    @Autowired
    OntologySpaceRepository ontologySpaceRepository;
    @Override
    public void handleCreateCdc(OntologySpace after) {
        EsOntologySpaceDTO esOntologySpaceDTO = OntologySpaceConverter.convert(after);
        ontologySpaceRepository.save(esOntologySpaceDTO);
    }

    @Override
    public boolean handleUpdateCdc(OntologySpace before, OntologySpace after) {
        return false;
    }

    @Override
    public boolean handleDeleteCdc(OntologySpace before) {
        return false;
    }
}
