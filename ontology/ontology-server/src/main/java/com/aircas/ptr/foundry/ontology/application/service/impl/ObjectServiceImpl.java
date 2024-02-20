package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.aircas.ptr.foundry.ontology.entity.vo.DirectoryItemVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyPropertyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ObjectServiceImpl implements ObjectService {

    @Resource
    private final OntologyPropertyMapper ontologyPropertyMapper;

    @Override
    public List<DirectoryItemVO> queryDirectories(String ontologyUniqueIdentifier) {
        List<OntologyProperty> ontologyPropertyList = ontologyPropertyMapper.selectByOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        System.out.println("fuck");
        System.out.println(ontologyPropertyList.size());
        OntologyProperty primaryKeyProperty = null;
        OntologyProperty titleKeyProperty = null;
        for(OntologyProperty property: ontologyPropertyList) {
            if (property.getIsPrimaryKey() == 1) {
                primaryKeyProperty = property;
            }
            if (property.getIsTitleKey() == 1) {
                titleKeyProperty = property;
            }
        }
        assert (primaryKeyProperty != null);
        assert (titleKeyProperty != null);
        return null;
    }
}
