package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.application.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OntologyPropertyServiceImpl implements OntologyPropertyService {

    private final OntologyPropertyMapper ontologyPropertyMapper;

    @Override
    public Integer add(OntologyPropertyBO ontologyPropertyBO) {
        OntologyProperty ontologyProperty = new OntologyProperty();
        BeanUtils.copyProperties(ontologyPropertyBO,ontologyProperty);
        ontologyProperty.setCreateTime(new Date());
        return ontologyPropertyMapper.insert(ontologyProperty);
    }

    @Override
    public Integer batchAdd(List<OntologyPropertyBO> ontologyPropertyBOs) {
        for (OntologyPropertyBO bo : ontologyPropertyBOs) {
            this.add(bo);
        }
        //TODO: 这里需要修改返回正确的status
        return 1;
    }

    @Override
    public Integer batchUpdate(List<OntologyPropertyBO> ontologyPropertyBOs) {
        for (OntologyPropertyBO bo : ontologyPropertyBOs) {
            if (isExist(bo.getUniqueIdentifier())) {
                update(bo);
            } else {
                add(bo);
            }
        }
        //TODO: 这里需要修改返回正确的status
        return 1;
    }

    private Boolean isExist(String uniqueIdentifier) {
        return selectByUniqueIdentifier(uniqueIdentifier) != null;
    }


    @Override
    public Integer delete(String uniqueIdentifier) {
        return ontologyPropertyMapper.deleteByUniqueIdentifier(uniqueIdentifier);
    }

    @Override
    public Integer update(OntologyPropertyBO ontologyPropertyBO) {
        OntologyProperty ontologyProperty = new OntologyProperty();
        BeanUtils.copyProperties(ontologyPropertyBO, ontologyProperty);
        ontologyProperty.setUpdateTime(new Date());
        return ontologyPropertyMapper.updateSelective(ontologyProperty);
    }

    @Override
    public List<OntologyPropertyVO> selectByOntologyUniqueIdentifier(String uniqueIdentifier) {
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByOntologyUniqueIdentifier(uniqueIdentifier);
        List<OntologyPropertyVO> list = new ArrayList<>();
        for (OntologyProperty ontologyProperty : ontologyPropertyList){
            OntologyPropertyVO propertyVO = new OntologyPropertyVO();
            BeanUtils.copyProperties(ontologyProperty, propertyVO);
            list.add(propertyVO);
        }

        return list;
    }

    @Override
    public OntologyPropertyVO selectByUniqueIdentifier(String uniqueIdentifier) {
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByUniqueIdentifier(uniqueIdentifier);
        if (ontologyPropertyList.size() == 0) {
            return null;
        }
        OntologyPropertyVO propertyVO = new OntologyPropertyVO();
        BeanUtils.copyProperties(ontologyPropertyList.get(0),propertyVO);
        return  propertyVO;
    }
}
