package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.DuplicatedDataException;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyGroupBO;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupAddParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyGroup;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyGroupMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.var;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:04
 */

@Service
public class OntologyGroupServiceImpl extends ServiceImpl<OntologyGroupMapper, OntologyGroup> implements OntologyGroupService {

    @Resource
    private OntologyGroupMapper ontologyGroupMapper;

    @Autowired
    private OntologyMetaService ontologyMetaService;

    @Autowired
    private OntologyLinkGroupService ontologyLinkGroupService;

    @Override
    public List<OntologyGroupInfoVO> searchByKeyword(String keyword) {
        var list = list(new LambdaQueryWrapper<OntologyGroup>().like(OntologyGroup::getGroupName, keyword));
        return list.stream().map(v -> OntologyGroupInfoVO.builder().groupId(v.getGroupId()).groupName(v.getGroupName()).build())
                .collect(Collectors.toList());
    }



}
