package com.aircas.ptr.foundry.ontology.entity.service;


import com.aircas.ptr.foundry.ontology.common.param.EntityCopyParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityCreateParam;

public interface EntityTableService {

    void createEntities(EntityCreateParam entityCreateParam);

    void copyEntities(EntityCopyParam entityCopyParam);

    void deleteEntitiesByTableName(String tableName);
}
