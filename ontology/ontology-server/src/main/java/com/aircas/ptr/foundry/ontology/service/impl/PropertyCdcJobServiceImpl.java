package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologyPropertyConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyPropertyDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyPropertyRepository;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import com.aircas.ptr.foundry.ontology.service.PropertyCdcJobService;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PropertyCdcJobServiceImpl implements PropertyCdcJobService {
    @Autowired
    OntologyPropertyRepository ontologyPropertyRepository;
    @Autowired
    OntologyMetaMapper metaMapper;
    @Autowired
    OntologySpaceMapper spaceMapper;

    @Override
    public void handleCreateCdc(OntologyProperty after) {
        EsOntologyPropertyDTO esOntologyPropertyDTO = OntologyPropertyConverter.convert(after);
        enrichPropertyDTO(esOntologyPropertyDTO);
        ontologyPropertyRepository.save(esOntologyPropertyDTO);
        log.info("save data success :{}", JSONObject.toJSONString(esOntologyPropertyDTO));
    }

    @Override
    public boolean handleUpdateCdc(OntologyProperty before, OntologyProperty after) {
        if (after == null || after.getId() == null) {
            return false;
        }
        EsOntologyPropertyDTO esOntologyPropertyDTO = OntologyPropertyConverter.convert(after);
        enrichPropertyDTO(esOntologyPropertyDTO);
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

    private void enrichPropertyDTO(EsOntologyPropertyDTO dto) {
        if (dto.getOntologyUniqueIdentifier() == null) {
            return;
        }
        OntologyMeta meta = metaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, dto.getOntologyUniqueIdentifier()));
        if (meta == null) {
            return;
        }
        dto.setOntologyName(meta.getDisplayName());
        dto.setOntologySpaceId(meta.getOntologySpaceId());
        if (meta.getOntologySpaceId() != null) {
            OntologySpace space = spaceMapper.selectById(meta.getOntologySpaceId());
            if (space != null) {
                dto.setSpaceName(space.getDisplayName());
            }
        }
    }
}
