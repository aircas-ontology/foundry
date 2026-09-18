package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.ontology.converter.OntologyMetaConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyMetaDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyMetaRepository;
import com.aircas.ptr.foundry.ontology.service.MetaCdcJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MetaCdcJobServiceImpl implements MetaCdcJobService {
    @Autowired
    OntologyMetaRepository ontologyMetaRepository;
    @Override
    public void handleCreateCdc(OntologyMeta ontologyMeta) {
        EsOntologyMetaDTO esOntologyMetaDTO = OntologyMetaConverter.convert(ontologyMeta);
        ontologyMetaRepository.save(esOntologyMetaDTO);
    }

    @Override
    public boolean handleUpdateCdc(OntologyMeta before,OntologyMeta after) {

    }

    @Override
    public boolean handleDeleteCdc(OntologyMeta ontologyMeta) {

    }
}
