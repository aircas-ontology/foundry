package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.entity.vo.DirectoryItemVO;
import com.aircas.ptr.foundry.ontology.entity.vo.ObjectOneInfoVO;
import com.aircas.ptr.foundry.ontology.entity.vo.ObjectWithLinkedInfoVO;
import com.github.pagehelper.PageInfo;

import java.util.List;
import java.util.Map;

public interface ObjectService {

    PageInfo<DirectoryItemVO> queryDirectories(String ontologyUniqueIdentifier, Integer page, Integer size);

    ObjectOneInfoVO queryObjectByPrimaryKey(String ontologyUniqueIdentifier, String key);

    ObjectOneInfoVO queryObjectByApiAndPrimaryKey(String api, String key);

    ObjectWithLinkedInfoVO queryObjectWithLinkedInfoByPrimaryKey(String ontologyUniqueIdentifier, String key);

    ObjectWithLinkedInfoVO queryObjectWithLinkedInfoByApiAndPrimaryKey(String api, String key);

    PageInfo<Map<String, Object>> queryObjectList(String uniqueIdentifier, Integer page, Integer size);
}
