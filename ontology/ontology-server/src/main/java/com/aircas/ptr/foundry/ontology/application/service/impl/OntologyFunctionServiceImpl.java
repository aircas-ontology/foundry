package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.OntologyFunction;
import com.aircas.ptr.foundry.model.po.OntologyFunctionMappingIn;
import com.aircas.ptr.foundry.ontology.application.service.OntologyFunctionService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionMappingInBO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyFunctionMappingInMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyFunctionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@RequiredArgsConstructor
public class OntologyFunctionServiceImpl implements OntologyFunctionService {

    @Resource
    private final OntologyFunctionMapper ontologyFunctionMapper;

    @Resource
    private final OntologyFunctionMappingInMapper ontologyFunctionMappingInMapper;


    @Override
    public int save(OntologyFunctionBo ontologyFunctionBo) {
        OntologyFunction ontologyFunction = new OntologyFunction();
        BeanUtils.copyProperties(ontologyFunctionBo, ontologyFunction);
        int status = ontologyFunctionMapper.insert(ontologyFunction);
        long id = ontologyFunction.getId();
        for (OntologyFunctionMappingInBO mappingInBO: ontologyFunctionBo.getMappingInList()) {
            OntologyFunctionMappingIn mappingIn = new OntologyFunctionMappingIn();
            BeanUtils.copyProperties(mappingInBO, mappingIn);
            mappingIn.setOntologyFunctionId(id);
            status = ontologyFunctionMappingInMapper.insert(mappingIn);
        }
        return status;
    }
}
