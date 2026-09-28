package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologyLinkGroupConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyLinkGroupDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyLinkGroupRepository;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
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
    @Autowired
    OntologySpaceMapper spaceMapper;

    @Override
    public void handleCreateCdc(OntologyLinkGroup after) {
        EsOntologyLinkGroupDTO esOntologyLinkGroupDTO = OntologyLinkGroupConverter.convert(after);
        enrichLinkGroupDTO(esOntologyLinkGroupDTO);
        ontologyLinkGroupRepository.save(esOntologyLinkGroupDTO);
        log.info("save data success :{}", JSONObject.toJSONString(esOntologyLinkGroupDTO));
    }

    @Override
    public boolean handleUpdateCdc(OntologyLinkGroup before, OntologyLinkGroup after) {
        if (after == null || after.getId() == null) {
            return false;
        }
        EsOntologyLinkGroupDTO esOntologyLinkGroupDTO = OntologyLinkGroupConverter.convert(after);
        enrichLinkGroupDTO(esOntologyLinkGroupDTO);
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

    private void enrichLinkGroupDTO(EsOntologyLinkGroupDTO dto) {
        if (dto.getOntologySpaceId() == null) {
            return;
        }
        OntologySpace space = spaceMapper.selectById(dto.getOntologySpaceId().intValue());
        if (space != null) {
            dto.setSpaceName(space.getDisplayName());
        }
    }
}
