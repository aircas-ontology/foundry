package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.exception.DuplicatedDataException;
import com.aircas.ptr.foundry.model.po.OntologyGroup;
import com.aircas.ptr.foundry.ontology.application.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyGroupBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkCountVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyGroupVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.param.OntologyGroupAddParam;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
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
public class OntologyGroupServiceImpl implements OntologyGroupService {

    @Resource
    private OntologyGroupMapper ontologyGroupMapper;

    @Autowired
    private OntologyMetaService ontologyMetaService;

    @Autowired
    private OntologyLinkGroupService ontologyLinkGroupService;

    @Override
    public Integer add(OntologyGroupAddParam param) {
        int count = ontologyGroupMapper.selectByGroupName(param.getGroupName());
        if (count != 0) {
            throw new DuplicatedDataException("本体分组已存在");
        }

        Date now = new Date();
        OntologyGroup ontologyGroup = new OntologyGroup();
        BeanUtils.copyProperties(param, ontologyGroup);
        ontologyGroup.setGroupId(UUID.randomUUID().toString());
        ontologyGroup.setStatus(1);
        ontologyGroup.setCreateTime(now);
        ontologyGroup.setUpdateTime(now);
        count = ontologyGroupMapper.insertSelective(ontologyGroup);
        return count;
    }

    @Override
    public Integer delete(List<Long> ids) {
        return ontologyGroupMapper.deleteByIds(ids);
    }

    @Override
    public Integer update(OntologyGroupBO ontologyGroupBO) {
        OntologyGroup ontologyGroup = ontologyGroupMapper.selectByPrimaryKey(ontologyGroupBO.getId());
        BeanUtils.copyProperties(ontologyGroupBO, ontologyGroup);
        ontologyGroup.setUpdateTime(new Date());
        return ontologyGroupMapper.updateByPrimaryKeySelective(ontologyGroup);
    }

    @Override
    public OntologyGroupVO getOntologyGroupById(Long id) {
        OntologyGroup ontologyGroup = ontologyGroupMapper.selectByPrimaryKey(id);
        OntologyGroupVO ontologyGroupVO = new OntologyGroupVO();
        BeanUtils.copyProperties(ontologyGroup, ontologyGroupVO);
        return ontologyGroupVO;
    }

    @Override
    public PageInfo<OntologyGroupVO> list(Integer page, Integer size) {

        PageHelper.startPage(page, size);
        PageInfo<OntologyGroup> pageInfo = new PageInfo<>(ontologyGroupMapper.selectAll());
        List<OntologyGroupVO> collect = pageInfo.getList().stream().map(item -> {
            OntologyGroupVO ontologyGroupVO = new OntologyGroupVO();
            BeanUtils.copyProperties(item, ontologyGroupVO);
            ontologyGroupVO.setCount(ontologyMetaService.countByGroup(item.getGroupId()));
            return ontologyGroupVO;
        }).collect(Collectors.toList());
        PageInfo<OntologyGroupVO> pageResult = new PageInfo<>();
        BeanUtils.copyProperties(pageInfo, pageResult);
        pageResult.setList(collect);
        return pageResult;
    }

    @Override
    public OntologyLinkGraphVO getOntologyGroupLinks(String id) {

        List<OntologyMetaVO> metaVOs = ontologyMetaService.listOntologiesByGroup(id);
        if (metaVOs.equals(null) || metaVOs.isEmpty()) {
            return null;
        }
        List<OntologyLinkCountVO> links = new ArrayList<>();
        ontologyLinkGroupService.getLinkByOntologies(metaVOs).stream().forEach(link -> {
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
    public PageInfo<OntologyGroupVO> search(String keyword, Integer page, Integer size) {

        PageHelper.startPage(page, size);
        PageInfo<OntologyGroup> pageInfo = new PageInfo<>(ontologyGroupMapper.searchByKeyword(keyword));
        List<OntologyGroupVO> collect = pageInfo.getList().stream().map(item -> {
            OntologyGroupVO ontologyGroupVO = new OntologyGroupVO();
            BeanUtils.copyProperties(item, ontologyGroupVO);
            return ontologyGroupVO;
        }).collect(Collectors.toList());
        PageInfo<OntologyGroupVO> pageResult = new PageInfo<>(collect);
        BeanUtils.copyProperties(pageInfo, pageResult);
        return pageResult;
    }
}
