package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.entity.vo.DirectoryItemVO;

import java.util.List;

public interface ObjectService {

    List<DirectoryItemVO> queryDirectories(String ontologyUniqueIdentifier);
}
