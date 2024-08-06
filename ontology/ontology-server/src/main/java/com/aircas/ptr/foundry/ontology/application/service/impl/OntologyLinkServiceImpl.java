package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultGenerator;
import com.aircas.ptr.foundry.model.po.OntologyChildLink;
import com.aircas.ptr.foundry.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.application.service.OntologyLinkService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyChildLinkVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkGroupVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyChildLinkMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
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
public class OntologyLinkServiceImpl implements OntologyLinkService {

    @Resource
    private OntologyLinkGroupMapper ontologyLinkGroupMapper;

    @Resource
    private OntologyChildLinkMapper ontologyChildLinkMapper;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

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
    public RestResult deleteLinkByUniqueIdentifier(String uniqueIdentifier) {
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

        Map<String, OntologyMeta> ontologyMetaMap = ontologyMetaMapper
                .selectByUniqueIdentifiers(ontologyUniqueIdentifiers)
                .stream()
                .map((OntologyMeta ontologyMeta) -> getEntry(ontologyMeta))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        linkGroupVOList.forEach((ontologyLinkGroupVO) -> {
            OntologyMeta ontologyFrom = ontologyMetaMap.get(ontologyLinkGroupVO.getOntologyUniqueIdentifierFrom());
            if (ontologyFrom != null) {
                ontologyLinkGroupVO.setOntologyNameFrom(ontologyFrom.getDisplayName());
            }
            OntologyMeta ontologyTo = ontologyMetaMap.get(ontologyLinkGroupVO.getOntologyUniqueIdentifierTo());
            if (ontologyTo != null) {
                ontologyLinkGroupVO.setOntologyNameTo(ontologyTo.getDisplayName());
            }
        });

    }

    private Map.Entry<String, OntologyMeta> getEntry(OntologyMeta ontologyMeta) {
        return new Map.Entry() {
            @Override
            public Object getKey() {
                return ontologyMeta.getUniqueIdentifier();
            }

            @Override
            public Object getValue() {
                return ontologyMeta;
            }

            @Override
            public Object setValue(Object value) {
                return ontologyMeta;
            }
        };
    }

}
