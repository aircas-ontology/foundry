package com.aircas.ptr.foundry.ontology.entity.service;

import com.aircas.ptr.foundry.ontology.common.vo.EntityRelationVO;

import java.util.List;

public interface EntityNodeService {

    void createEntityRelation(String entityFrom, String entityTo, String relationType);

    List<EntityRelationVO> queryRelations(String tableName, Object primaryKeyValue);
}
