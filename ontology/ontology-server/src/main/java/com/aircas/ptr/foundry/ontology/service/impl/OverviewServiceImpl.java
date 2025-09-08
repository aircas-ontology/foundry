package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.DbStatus;
import com.aircas.ptr.foundry.ontology.model.vo.OverviewCountVo;
import com.aircas.ptr.foundry.ontology.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author wangweigang
 * @description 概览service的实现类
 */
@Service
public class OverviewServiceImpl implements OverviewService {

    @Autowired
    private OntologyMetaService ontologyMetaService;

    @Autowired
    private OntologyLinkGroupService ontologyLinkGroupService;

    @Autowired
    private OntologyActionService ontologyActionService;

    @Autowired
    private FunctionService functionService;

    @Override
    public OverviewCountVo getCountNotDel() {

        // 本体统计
        OverviewCountVo overviewCountVo = new OverviewCountVo();
        Integer ontologyCount = ontologyMetaService.getCountByStatus(DbStatus.NOT_DELETED.getValue());
        overviewCountVo.setOntologyCount(Math.toIntExact(ontologyCount));

        int linkCount = ontologyLinkGroupService.getCountByStatus(DbStatus.NOT_DELETED.getValue());
        overviewCountVo.setLinkCount(linkCount);

        int actionCount = ontologyActionService.getCountByStatus(DbStatus.NOT_DELETED.getValue());
        overviewCountVo.setActionCount(actionCount);

        int functionCount = functionService.getCountByStatus(DbStatus.NOT_DELETED.getValue());
        overviewCountVo.setFunctionCount(functionCount);

        return overviewCountVo;
    }
}
