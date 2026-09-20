package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologyLinkGroupConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyLinkGroupDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyLinkGroupRepository;
import com.aircas.ptr.foundry.ontology.service.LinkGroupCdcJobService;
import com.alibaba.fastjson2.JSONObject;
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
        log.info("save data success :{}", JSONObject.toJSONString(esOntologyLinkGroupDTO));
    }

    @Override
    public boolean handleUpdateCdc(OntologyLinkGroup before, OntologyLinkGroup after) {
        if (after == null || after.getId() == null) {
            return false;
        }
        // 关系分组文档按主键 id upsert；分组定义变更不影响其它索引，无需级联回刷
        EsOntologyLinkGroupDTO esOntologyLinkGroupDTO = OntologyLinkGroupConverter.convert(after);
        ontologyLinkGroupRepository.save(esOntologyLinkGroupDTO);
        log.info("update data success :{}", JSONObject.toJSONString(esOntologyLinkGroupDTO));
        return true;
    }

    @Override
    public boolean handleDeleteCdc(OntologyLinkGroup before) {
        if (before == null || before.getId() == null) {
            return false;
        }
        ontologyLinkGroupRepository.deleteById(before.getId());
        log.info("delete data success, link group id:{}", before.getId());
        return true;
    }
}
