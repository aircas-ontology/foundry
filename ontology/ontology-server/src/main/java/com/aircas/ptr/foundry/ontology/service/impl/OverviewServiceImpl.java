package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.DbStatus;
import com.aircas.ptr.foundry.ontology.model.vo.OverviewCountVO;
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
    public OverviewCountVO getCount() {

        /**
         * todo
         */
        return OverviewCountVO.builder()
                .functionCount(functionService.count())
                .ontologyCount(ontologyMetaService.count())
                .build();

    }
}
