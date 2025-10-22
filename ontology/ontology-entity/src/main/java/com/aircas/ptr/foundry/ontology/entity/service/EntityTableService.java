package com.aircas.ptr.foundry.ontology.entity.service;


import com.aircas.ptr.foundry.ontology.common.param.EntityColumnCreateParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityCopyParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityDetailQueryParam;
import com.aircas.ptr.foundry.ontology.common.vo.EntityDetailVO;
import com.aircas.ptr.foundry.ontology.common.vo.EntityVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public interface EntityTableService {

    void createColumns(EntityColumnCreateParam param);

    void createEntities(EntityCreateParam entityCreateParam);

    void copyEntities(EntityCopyParam entityCopyParam);

    void deleteEntitiesByTableName(String tableName);

    void deleteNodesByTableName(String entityTableName);

    Page<EntityVO> queryEntitiesByTableName(String tableName, Integer pageNum, Integer pageSize);

    List<EntityDetailVO> queryEntityDetail(EntityDetailQueryParam param);

}
