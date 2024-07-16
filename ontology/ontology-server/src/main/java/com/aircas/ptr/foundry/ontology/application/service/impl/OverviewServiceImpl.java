package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.constant.DbStatus;
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.application.service.OverviewService;
import com.aircas.ptr.foundry.ontology.entity.vo.OverviewCountVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author wangweigang
 * @description 概览service的实现类
 */
@Service
public class OverviewServiceImpl implements OverviewService {

    @Autowired
    OntologyMetaService ontologyMetaService;

    @Override
    public OverviewCountVo getCountNotDel() {
        OverviewCountVo overviewCountVo = new OverviewCountVo();
        Integer ontologyCount = ontologyMetaService.getCountByStatus(DbStatus.NOT_DELETED.getValue());
        overviewCountVo.setOntologyCount(Math.toIntExact(ontologyCount));
        return overviewCountVo;
    }
}
