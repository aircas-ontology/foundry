package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultGenerator;
import com.aircas.ptr.foundry.common.constant.OntologyLinkMappingEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.ontology.client.EntityClient;
import com.aircas.ptr.foundry.ontology.common.param.EntityRelationCreateParam;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyChildLink;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyChildLinkMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:03
 */

@Service
@Slf4j
public class OntologyLinkGroupServiceImpl extends ServiceImpl<OntologyLinkGroupMapper, OntologyLinkGroup> implements OntologyLinkGroupService {

    @Resource
    private OntologyLinkGroupMapper ontologyLinkGroupMapper;

    @Resource
    private OntologyChildLinkMapper ontologyChildLinkMapper;

    @Resource
    private OntologyMetaService ontologyMetaService;

    @Resource
    private EntityClient entityClient;


    @Override
    @Transactional(value = "mainTransactionManager")
    public void createLink(OntologyLinkCreateParam linkCreateParam) {
        save(OntologyLinkGroup.builder()
                .uniqueIdentifier(IdGenerator.generateUUID())
                .status(Status.ENABLE.getValue())
                .ontologyUniqueIdentifierFrom(linkCreateParam.getOntologyUniqueIdentifierFrom())
                .ontologyUniqueIdentifierTo(linkCreateParam.getOntologyUniqueIdentifierTo())
                .propertyUniqueIdentifierFrom(linkCreateParam.getPropertyUniqueIdentifierFrom())
                .propertyUniqueIdentifierTo(linkCreateParam.getPropertyUniqueIdentifierTo())
                .name(linkCreateParam.getName())
                .mapping(linkCreateParam.getMapping().getValue())
                .build());

        var fromMeta = ontologyMetaService.getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, linkCreateParam.getOntologyUniqueIdentifierFrom()));
        var toMeta = ontologyMetaService.getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, linkCreateParam.getOntologyUniqueIdentifierTo()));


        entityClient.createEntityRelation(EntityRelationCreateParam.builder()
                .entityTableFrom(fromMeta.getApiName())
                .entityTableTo(toMeta.getApiName())
                .relationType(linkCreateParam.getName())
                .build());
    }

    @Override
    public List<OntologyLinkInfoVO> getLinksByGroupId(String groupId) {
        var ids = ontologyMetaService.list(new LambdaQueryWrapper<OntologyMeta>().like(OntologyMeta::getMetaGroupId, groupId))
                .stream().map(v -> v.getUniqueIdentifier()).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(ids)) {
            return Lists.newArrayList();
        }
        var links = list(new LambdaQueryWrapper<OntologyLinkGroup>().in(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ids)
                .or().in(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ids));

        if (CollectionUtils.isEmpty(links)) {
            return Lists.newArrayList();
        }
        return buildLinkInfo(links);
    }

    @Override
    public List<OntologyLinkInfoVO> getLinksByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) {
        var links = list(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifier));

        if (CollectionUtils.isEmpty(links)) {
            return Lists.newArrayList();
        }
        return buildLinkInfo(links);
    }

    private List<OntologyLinkInfoVO> buildLinkInfo(List<OntologyLinkGroup> links) {
        var idList = links.stream().flatMap(l -> Stream.of(l.getOntologyUniqueIdentifierFrom(), l.getOntologyUniqueIdentifierTo())).collect(Collectors.toList());

        var metaMap = ontologyMetaService.list(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, idList))
                .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v));

        return links.stream().map(link -> {
            var from = metaMap.get(link.getOntologyUniqueIdentifierFrom());
            var to = metaMap.get(link.getOntologyUniqueIdentifierTo());
            return OntologyLinkInfoVO.builder()
                    .mapping(OntologyLinkMappingEnum.getByValue(link.getMapping()))
                    .createTime(link.getCreateTime())
                    .updateTime(link.getUpdateTime())
                    .name(link.getName())
                    .uniqueIdentifier(link.getUniqueIdentifier())
                    .ontologyUniqueIdentifierFrom(from.getUniqueIdentifier())
                    .ontologyUniqueIdentifierTo(to.getUniqueIdentifier())
                    .ontologyIconFrom(from.getIcon())
                    .ontologyIconTO(to.getIcon())
                    .ontologyNameFrom(from.getDisplayName())
                    .ontologyNameTo(to.getDisplayName())
                    .propertyUniqueIdentifierFrom(link.getPropertyUniqueIdentifierFrom())
                    .propertyUniqueIdentifierTo(link.getPropertyUniqueIdentifierTo())
                    .build();
        }).collect(Collectors.toList());

    }

    @Override
    public Integer add(OntologyLinkGroupBo ontologyLinkGroupBo) {
        OntologyLinkGroupBo normalizedLinkGroupBo = ontologyLinkGroupBo.normalized();
        OntologyChildLink forwardLink = new OntologyChildLink();
        BeanUtils.copyProperties(normalizedLinkGroupBo.getForwardLink(), forwardLink);
        int count = ontologyChildLinkMapper.insert(forwardLink);
        if (count == 0) {
            return 0;
        }

        OntologyChildLink backwardLink = new OntologyChildLink();
        BeanUtils.copyProperties(normalizedLinkGroupBo.getBackwardLink(), backwardLink);
        count = ontologyChildLinkMapper.insert(backwardLink);
        if (count == 0) {
            return 0;
        }
        OntologyLinkGroup ontologyLinkGroup = new OntologyLinkGroup();
        BeanUtils.copyProperties(normalizedLinkGroupBo, ontologyLinkGroup);
        ontologyLinkGroup.setStatus(1);
        ontologyLinkGroup.setCreateTime(new Date());
        ontologyLinkGroup.setForwardChildLinkId(forwardLink.getId());
        ontologyLinkGroup.setBackwardChildLinkId(backwardLink.getId());
        count = ontologyLinkGroupMapper.insert(ontologyLinkGroup);
        return 0;
    }


    @Override
    public List<OntologyLinkGroupVO> getAll() {
        List<OntologyLinkGroup> linkGroups = ontologyLinkGroupMapper.selectAll();
        List<OntologyLinkGroupVO> result = new ArrayList<>();
        for (OntologyLinkGroup linkGroup : linkGroups) {
            OntologyLinkGroupVO linkGroupVO = new OntologyLinkGroupVO();
            BeanUtils.copyProperties(linkGroup, linkGroupVO);
            setChildLinks(linkGroupVO, linkGroup.getForwardChildLinkId(), linkGroup.getBackwardChildLinkId());
            result.add(linkGroupVO);
        }
        addOntologyNames(result);
        return result;
    }

    @Override
    public List<OntologyLinkGroupVO> getLinkByOntologies(List<OntologyMetaVO> metaVOs) {

        List<String> ontologyIds = metaVOs.stream().map(meta -> meta.getUniqueIdentifier()).collect(Collectors.toList());
        List<OntologyLinkGroup> linkGroups = ontologyLinkGroupMapper.getLinkByOntologies(ontologyIds);
        List<OntologyLinkGroupVO> result = new ArrayList<>();
        for (OntologyLinkGroup linkGroup : linkGroups) {
            OntologyLinkGroupVO linkGroupVO = new OntologyLinkGroupVO();
            BeanUtils.copyProperties(linkGroup, linkGroupVO);
            setChildLinks(linkGroupVO, linkGroup.getForwardChildLinkId(), linkGroup.getBackwardChildLinkId());
            linkGroupVO.setOntologyNameTo(metaVOs.stream().filter(meta -> meta.getUniqueIdentifier().equals(linkGroup.getOntologyUniqueIdentifierTo())).findFirst().get().getDisplayName());
            linkGroupVO.setOntologyNameFrom(metaVOs.stream().filter(meta -> meta.getUniqueIdentifier().equals(linkGroup.getOntologyUniqueIdentifierFrom())).findFirst().get().getDisplayName());
            linkGroupVO.setOntologyIconTO(metaVOs.stream().filter(meta -> meta.getUniqueIdentifier().equals(linkGroup.getOntologyUniqueIdentifierTo())).findFirst().get().getIcon());
            linkGroupVO.setOntologyIconFrom(metaVOs.stream().filter(meta -> meta.getUniqueIdentifier().equals(linkGroup.getOntologyUniqueIdentifierFrom())).findFirst().get().getIcon());
            result.add(linkGroupVO);
        }
        return result;
    }

    @Override
    public OntologyLinkGraphVO getLinkGraphByOntologyUniqueIdentifier(String oId) {

        List<OntologyLinkGroup> linkGroups = new ArrayList<>();
        List<OntologyLinkGroup> forwardOntologyLinkGroups = ontologyLinkGroupMapper.selectByOntologyUniqueIdentifierFrom(oId);
        linkGroups.addAll(forwardOntologyLinkGroups);

        List<OntologyLinkGroup> backwardOntologyLinkGroups = ontologyLinkGroupMapper.selectByOntologyUniqueIdentifierTo(oId);
        linkGroups.addAll(backwardOntologyLinkGroups.stream().map(OntologyLinkGroup::revertForwardToBackward).collect(Collectors.toList()));
        List<String> metaIds = new ArrayList<>();
        linkGroups.stream().forEach(linkGroup -> {
            if (!metaIds.contains(linkGroup.getOntologyUniqueIdentifierTo())) {
                metaIds.add(linkGroup.getOntologyUniqueIdentifierTo());
            }
            if (!metaIds.contains(linkGroup.getOntologyUniqueIdentifierFrom())) {
                metaIds.add(linkGroup.getOntologyUniqueIdentifierFrom());
            }
        });
        List<OntologyMetaVO> metaVOs = ontologyMetaService.selectByUniqueIdentifiers(metaIds);
        if (metaVOs.equals(null) || metaVOs.isEmpty()) {
            return null;
        }
        List<OntologyLinkCountVO> links = new ArrayList<>();
        getLinkByOntologies(metaVOs).stream().forEach(link -> {
            OntologyLinkCountVO ontologyLinkCountVO = new OntologyLinkCountVO();
            ontologyLinkCountVO.setOntologyId1(link.getOntologyUniqueIdentifierFrom());
            ontologyLinkCountVO.setOntologyName1(link.getOntologyNameFrom());
            ontologyLinkCountVO.setOntologyId2(link.getOntologyUniqueIdentifierTo());
            ontologyLinkCountVO.setOntologyName2(link.getOntologyNameTo());
            ontologyLinkCountVO.setLinkCount(1);
            if (links.contains(ontologyLinkCountVO)) {
                links.remove(ontologyLinkCountVO);
                ontologyLinkCountVO.setLinkCount(2);
            }
            links.add(ontologyLinkCountVO);
        });
        return new OntologyLinkGraphVO(metaVOs, links);
    }

    @Override
    public List<OntologyLinkGroup> selectByOntologyUniqueIdentifierFrom(String ontologyUniqueIdentifier) {

        return ontologyLinkGroupMapper.selectByOntologyUniqueIdentifierFrom(ontologyUniqueIdentifier);
    }

    @Override
    public List<OntologyLinkGroup> selectByOntologyUniqueIdentifierTo(String ontologyUniqueIdentifier) {

        return ontologyLinkGroupMapper.selectByOntologyUniqueIdentifierTo(ontologyUniqueIdentifier);
    }

    @Override
    public OntologyLinkGroup selectByUniqueIdentifier(String uniqueIdentifier) {

        return ontologyLinkGroupMapper.selectByUniqueIdentifier(uniqueIdentifier);
    }

    @Override
    public int getCountByStatus(int status) {

        return ontologyLinkGroupMapper.countByStatus(status);
    }

    private void setChildLinks(OntologyLinkGroupVO linkGroupVO, long forwardChildLinkId, long backwardChildLinkId) {
        OntologyChildLink forwardChildLink = ontologyChildLinkMapper.selectByPrimaryKey(forwardChildLinkId);
        OntologyChildLinkVO forwardChildLinkVO = new OntologyChildLinkVO();
        BeanUtils.copyProperties(forwardChildLink, forwardChildLinkVO);
        linkGroupVO.setForwardLink(forwardChildLinkVO);

        OntologyChildLink backwardChildLink = ontologyChildLinkMapper.selectByPrimaryKey(backwardChildLinkId);
        OntologyChildLinkVO backwardChildLinkVO = new OntologyChildLinkVO();
        BeanUtils.copyProperties(backwardChildLink, backwardChildLinkVO);
        linkGroupVO.setBackwardLink(backwardChildLinkVO);
    }


    @Override
    public RestResult deleteLinkByOntologyUniqueIdentifier(String uniqueIdentifier) {
        OntologyLinkGroup ontologyLinkGroup = ontologyLinkGroupMapper.selectByUniqueIdentifier(uniqueIdentifier);
        if (ontologyLinkGroup != null) {
            ontologyChildLinkMapper.deleteByPrimaryKey(ontologyLinkGroup.getForwardChildLinkId());
            ontologyChildLinkMapper.deleteByPrimaryKey(ontologyLinkGroup.getBackwardChildLinkId());
            ontologyLinkGroupMapper.deleteByUniqueIdentifier(uniqueIdentifier);
            return ResultGenerator.genSuccessResult();
        }
        return ResultGenerator.genFailResult("未查询到该数据！");
    }


    private void addOntologyNames(List<OntologyLinkGroupVO> linkGroupVOList) {
        List<String> ontologyUniqueIdentifiersFrom = linkGroupVOList
                .stream()
                .map((OntologyLinkGroupVO linkGroupVo) -> linkGroupVo.getOntologyUniqueIdentifierFrom())
                .collect(Collectors.toList());
        List<String> ontologyUniqueIdentifiersTo = linkGroupVOList
                .stream()
                .map((OntologyLinkGroupVO linkGroupVo) -> linkGroupVo.getOntologyUniqueIdentifierTo())
                .collect(Collectors.toList());
        List<String> ontologyUniqueIdentifiers = new ArrayList<>();
        ontologyUniqueIdentifiers.addAll(ontologyUniqueIdentifiersFrom);
        ontologyUniqueIdentifiers.addAll(ontologyUniqueIdentifiersTo);

        if (ontologyUniqueIdentifiers.isEmpty()) {
            return;
        }
        Map<String, OntologyMetaVO> ontologyMetaMap = ontologyMetaService
                .selectByUniqueIdentifiers(ontologyUniqueIdentifiers)
                .stream()
                .map((OntologyMetaVO vo) -> getEntry(vo))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        linkGroupVOList.forEach((ontologyLinkGroupVO) -> {
            OntologyMetaVO ontologyFrom = ontologyMetaMap.get(ontologyLinkGroupVO.getOntologyUniqueIdentifierFrom());
            if (ontologyFrom != null) {
                ontologyLinkGroupVO.setOntologyNameFrom(ontologyFrom.getDisplayName());
                ontologyLinkGroupVO.setOntologyIconFrom(ontologyFrom.getIcon());
            }
            OntologyMetaVO ontologyTo = ontologyMetaMap.get(ontologyLinkGroupVO.getOntologyUniqueIdentifierTo());
            if (ontologyTo != null) {
                ontologyLinkGroupVO.setOntologyNameTo(ontologyTo.getDisplayName());
                ontologyLinkGroupVO.setOntologyIconTO(ontologyTo.getIcon());
            }
        });
        log.info("");
    }

    private Map.Entry<String, OntologyMetaVO> getEntry(OntologyMetaVO ontologyMetaVO) {
        return new Map.Entry() {
            @Override
            public Object getKey() {
                return ontologyMetaVO.getUniqueIdentifier();
            }

            @Override
            public Object getValue() {
                return ontologyMetaVO;
            }

            @Override
            public Object setValue(Object value) {
                return ontologyMetaVO;
            }
        };
    }

}
