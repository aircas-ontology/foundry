package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.enums.SpaceTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.OverviewCountVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.OverviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

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


    @Resource
    private OntologySpaceMapper spaceMapper;

    @Override
    public OverviewCountVO getCount(Integer type) {
        // spaceCount 按类型统计：0=空间，1=主题/子空间
        var spaceType = SpaceTypeEnum.normalize(type);
        return OverviewCountVO.builder()
                .spaceCount(Math.toIntExact(spaceMapper.selectCount(new LambdaQueryWrapper<OntologySpace>().eq(OntologySpace::getType, spaceType))))
                .actionCount(Math.toIntExact(ontologyActionMapper.selectCount(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getStatus, Status.ENABLE.getValue()))))
                .linkCount(Math.toIntExact(ontologyLinkGroupMapper.selectCount(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue()))))
                .propertyCount(Math.toIntExact(propertyMapper.selectCount(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getStatus, Status.ENABLE.getValue()))))
                .groupCount(Math.toIntExact(groupMapper.selectCount(new QueryWrapper<>())))
                .actionSchedulingCount(Math.toIntExact(ruleMapper.selectCount(new QueryWrapper<>()) + taskMapper.selectCount(new QueryWrapper<>())))
                .functionCount(Math.toIntExact(functionMapper.selectCount(new LambdaQueryWrapper<Function>().eq(Function::getStatus, Status.ENABLE.getValue()))))
                .ontologyCount(Math.toIntExact(ontologyMetaMapper.selectCount(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))))
                .build();
    }
}
