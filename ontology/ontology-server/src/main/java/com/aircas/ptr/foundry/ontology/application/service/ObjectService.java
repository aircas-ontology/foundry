package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.entity.vo.DirectoryItemVO;
import com.aircas.ptr.foundry.ontology.entity.vo.ObjectValueVo;
import com.aircas.ptr.foundry.ontology.entity.vo.ObjectWithLinkedInfoVO;
import com.github.pagehelper.PageInfo;

import java.util.List;

public interface ObjectService {

    PageInfo<DirectoryItemVO> queryDirectories(String ontologyUniqueIdentifier, Integer page, Integer size);

    ObjectValueVo queryObjectByPrimaryKey(String ontologyUniqueIdentifier, String key);

    ObjectValueVo queryObjectByApiAndPrimaryKey(String api, String key);

    ObjectWithLinkedInfoVO queryObjectWithLinkedInfoByPrimaryKey(String ontologyUniqueIdentifier, String key);

    ObjectWithLinkedInfoVO queryObjectWithLinkedInfoByApiAndPrimaryKey(String api, String key);
}
