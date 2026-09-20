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
        if (after == null || after.getId() == null) {
            return false;
        }
        // 属性文档按主键 id upsert；属性定义变更不影响实例文档内容，无需级联回刷
        EsOntologyPropertyDTO esOntologyPropertyDTO = OntologyPropertyConverter.convert(after);
        ontologyPropertyRepository.save(esOntologyPropertyDTO);
        log.info("update data success :{}", JSONObject.toJSONString(esOntologyPropertyDTO));
        return true;
    }

    @Override
    public boolean handleDeleteCdc(OntologyProperty before) {
        if (before == null || before.getId() == null) {
            return false;
        }
        ontologyPropertyRepository.deleteById(before.getId());
        log.info("delete data success, property id:{}", before.getId());
        return true;
    }
}
