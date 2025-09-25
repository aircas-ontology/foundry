package com.aircas.ptr.foundry.ontology.entity.service.impl;

import com.aircas.ptr.foundry.ontology.entity.model.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.entity.service.EntityTableService;
import lombok.var;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntityTableServiceImpl implements EntityTableService {

    @Override
    @Transactional
    public void createEntities(EntityCreateParam entityCreateParam) {
        var primaryDataSource = entityCreateParam.getPrimaryDataSource();
        var associateDataSources = entityCreateParam.getAssociateDataSources();
        // 创建实体表

        //

    }
}
