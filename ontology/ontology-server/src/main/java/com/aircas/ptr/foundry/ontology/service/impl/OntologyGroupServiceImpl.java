package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupUpdatedParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupInfoVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyGroupMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:04
 */

@Service
public class OntologyGroupServiceImpl extends ServiceImpl<OntologyGroupMapper, OntologyGroup> implements OntologyGroupService {

    @Autowired
    private OntologyMetaService ontologyMetaService;

    @Override
    public void createGroup(OntologyGroupCreateParam param) {
        save(OntologyGroup.builder()
                .groupName(param.getGroupName())
                .groupId(IdGenerator.generateUUID())
                .icon(param.getIconUrl())
                .status(Status.ENABLE.getValue())
                .description(param.getDescription())
                .build());
    }

    @Override
    public void deleteGroupById(String groupId) {
        var metaList = ontologyMetaService.list(new LambdaQueryWrapper<OntologyMeta>().like(OntologyMeta::getMetaGroupId, groupId));
        if (CollectionUtils.isEmpty(metaList)) {
            remove(new LambdaQueryWrapper<OntologyGroup>().eq(OntologyGroup::getGroupId, groupId));
        } else {
            throw new BusinessException("该组内存在本体，无法删除", ResultCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public List<OntologyGroupInfoVO> searchByKeyword(String keyword) {
        var groupName = StringUtils.isEmpty(keyword) ? "" : keyword;
        var list = list(new LambdaQueryWrapper<OntologyGroup>().like(OntologyGroup::getGroupName, groupName));
        return list.stream().map(v -> OntologyGroupInfoVO.builder()
                        .groupId(v.getGroupId())
                        .groupName(v.getGroupName())
                        .description(v.getDescription())
                        .icon(v.getIcon())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void updateGroup(OntologyGroupUpdatedParam param) {
        var group = getOne(new LambdaQueryWrapper<OntologyGroup>().eq(OntologyGroup::getGroupId, param.getGroupId()));
        group.setGroupName(param.getGroupName())
                .setDescription(param.getDescription())
                .setIcon(param.getIconUrl());
        updateById(group);
    }

    @Override
    public OntologyGroupInfoVO getGroupById(String groupId) {
        var group = getOne(new LambdaQueryWrapper<OntologyGroup>().eq(OntologyGroup::getGroupId, groupId));
        if (group == null) {
            return null;
        }
        return OntologyGroupInfoVO.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .description(group.getDescription())
                .icon(group.getIcon())
                .build();
    }
}
