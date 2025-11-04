package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.vo.OverviewCountVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.OverviewService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author wangweigang
 * @description 概览service的实现类
 */
@Service
public class OverviewServiceImpl implements OverviewService {

    @Resource
    private OntologyPropertyMapper propertyMapper;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private OntologyLinkGroupMapper ontologyLinkGroupMapper;

    @Resource
    private OntologyActionMapper ontologyActionMapper;

    @Resource
    private ActionHandleTaskMapper taskMapper;

    @Resource
    private ActionHandleRuleMapper ruleMapper;

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private OntologyGroupMapper groupMapper;

    @Override
    public OverviewCountVO getCount() {

        return OverviewCountVO.builder()
                .actionCount(ontologyActionMapper.selectCount(new QueryWrapper<>()))
                .linkCount(ontologyLinkGroupMapper.selectCount(new QueryWrapper<>()))
                .propertyCount(propertyMapper.selectCount(new QueryWrapper<>()))
                .groupCount(groupMapper.selectCount(new QueryWrapper<>()))
                .actionSchedulingCount(ruleMapper.selectCount(new QueryWrapper<>()) + taskMapper.selectCount(new QueryWrapper<>()))
                .functionCount(functionMapper.selectCount(new QueryWrapper<>()))
                .ontologyCount(ontologyMetaMapper.selectCount(new QueryWrapper<>()))
                .build();

    }
}
