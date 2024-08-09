package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultGenerator;
import com.aircas.ptr.foundry.model.po.OntologyChildLink;
import com.aircas.ptr.foundry.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.application.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyChildLinkMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyLinkGroupMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:03
 */

@Service
public class OntologyLinkGroupServiceImpl implements OntologyLinkGroupService {

    private static final Logger log = LoggerFactory.getLogger(OntologyLinkGroupServiceImpl.class);
    @Resource
    private OntologyLinkGroupMapper ontologyLinkGroupMapper;

    @Resource
    private OntologyChildLinkMapper ontologyChildLinkMapper;

    @Resource
    private OntologyMetaService ontologyMetaService;

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
    public List<OntologyLinkGroupVO> getLinkByOntologyUniqueIdentifier(String uniqueIdentifier) {
        List<OntologyLinkGroup> linkGroups = new ArrayList<>();
        List<OntologyLinkGroup> forwardOntologyLinkGroups = ontologyLinkGroupMapper.selectByOntologyUniqueIdentifierFrom(uniqueIdentifier);
        linkGroups.addAll(forwardOntologyLinkGroups);

        List<OntologyLinkGroup> backwardOntologyLinkGroups = ontologyLinkGroupMapper.selectByOntologyUniqueIdentifierTo(uniqueIdentifier);
        linkGroups.addAll(backwardOntologyLinkGroups.stream().map(OntologyLinkGroup::revertForwardToBackward).collect(Collectors.toList()));

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
