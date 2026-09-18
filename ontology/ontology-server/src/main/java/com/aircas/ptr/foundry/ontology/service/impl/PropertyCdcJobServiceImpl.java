package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologyPropertyConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyPropertyRepository;
import com.aircas.ptr.foundry.ontology.service.PropertyCdcJobService;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class PropertyCdcJobServiceImpl implements PropertyCdcJobService {
    @Autowired
    OntologyPropertyRepository ontologyPropertyRepository;
    @Override
    public void handleCreateCdc(OntologyProperty after) {
        EsOntologyPropertyDTO esOntologyPropertyDTO = OntologyPropertyConverter.convert(after);
        ontologyPropertyRepository.save(esOntologyPropertyDTO);
        log.info("save data success :{}", JSONObject.toJSONString(esOntologyPropertyDTO));
    }

    @Override
    public boolean handleUpdateCdc(OntologyProperty before, OntologyProperty after) {
        return false;
    }

    @Override
    public boolean handleDeleteCdc(OntologyProperty before) {
        return false;
    }
}
