package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.ontology.model.param.OntologyGroupAddParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupInfoVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyGroupMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
    public void createGroup(OntologyGroupAddParam param) {
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
        var list = list(new LambdaQueryWrapper<OntologyGroup>().like(OntologyGroup::getGroupName, keyword));
        return list.stream().map(v -> OntologyGroupInfoVO.builder().groupId(v.getGroupId()).groupName(v.getGroupName()).build())
                .collect(Collectors.toList());
    }


}
