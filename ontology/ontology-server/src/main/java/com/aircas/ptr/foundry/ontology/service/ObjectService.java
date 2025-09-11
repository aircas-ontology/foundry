package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.vo.DirectoryItemVO;
import com.aircas.ptr.foundry.ontology.model.vo.ObjectOneInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.ObjectWithLinkedInfoVO;
import com.aircas.ptr.foundry.ontology.model.param.FilterParam;
import com.aircas.ptr.foundry.ontology.model.param.QuerySortParam;
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

    int updateObjectData(String ontologyUniqueIdentifier,Map<String, Object> datamap,Map<String, Object> whereMap);

    PageInfo<Map<String, Object>> queryObjectByFilter(String ontologyApi, List<FilterParam> filter, Integer page, Integer size, List<QuerySortParam> sorts);

    List<Map<String, Object>> queryObjectByFilter(String ontologyApi, List<FilterParam> filter, List<QuerySortParam> sorts);

    PageInfo<Map<String, Object>> queryObjectByLink(String linkId, String dataOntologyId, ObjectOneInfoVO obj, Integer page, Integer size);
}
