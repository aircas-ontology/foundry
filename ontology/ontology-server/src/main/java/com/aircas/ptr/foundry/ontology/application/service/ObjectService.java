package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.entity.vo.DirectoryItemVO;
import com.aircas.ptr.foundry.ontology.entity.vo.PropertyValueVO;

import java.util.List;

public interface ObjectService {

    List<DirectoryItemVO> queryDirectories(String ontologyUniqueIdentifier);

    List<PropertyValueVO> queryObjectByPrimaryKey(String ontologyUniqueIdentifier, String key);
}
