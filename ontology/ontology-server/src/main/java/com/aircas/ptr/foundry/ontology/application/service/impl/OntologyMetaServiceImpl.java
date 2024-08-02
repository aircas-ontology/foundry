package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.exception.DuplicatedDataException;
import com.aircas.ptr.foundry.model.po.*;
import com.aircas.ptr.foundry.ontology.application.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.application.service.TableMetadataService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyGroupVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.entity.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.repository.dao.*;
import com.aircas.ptr.foundry.ontology.repository.param.OntologyMetaAddParam;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
//import java.util.Map;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 16:15
 */

@Service
public class OntologyMetaServiceImpl implements OntologyMetaService {

    private static final Logger log = LoggerFactory.getLogger(OntologyMetaServiceImpl.class);
    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Autowired
    private OntologyPropertyService ontologyPropertyService;

    @Autowired
    private TableMetadataService tableMetadataService;

    @Autowired
    private OntologyGroupMapper ontologyGroupMapper;

    @Override
    public Integer add(OntologyMetaAddParam param) {
        int count = ontologyMetaMapper.selectByDisplayName(param.getDisplayName());
        if (count != 0) {
            throw new DuplicatedDataException("本体名称已存在");
        }
        // 进行本体插入
        OntologyMeta ontologyMeta = new OntologyMeta();
        BeanUtils.copyProperties(param, ontologyMeta);
        ontologyMeta.setStatus(1);
        ontologyMeta.setCreateTime(new Date());
        count = ontologyMetaMapper.insertSelective(ontologyMeta);
        // 如果datasource不为空，插入本体属性
        if (param.getIsMapAllParam()) {
            List<TableColumnDescVO> columns = tableMetadataService.getColumns(param.getBackingDatasourceId());
            List<OntologyPropertyBO> collect = columns.stream().map(column -> {
                OntologyPropertyBO property = new OntologyPropertyBO();
                property.setOntologyUniqueIdentifier(param.getUniqueIdentifier());
                property.setApiName(column.getColumnName());
                property.setDatasourceColumnName(column.getColumnName());
                property.setDatasourceId(param.getBackingDatasourceId());
                property.setDescription(column.getColumnName());
                property.setDisplayName(column.getColumnName());
                if (column.getColumnName().equals(param.getTitleKey())) {
                    property.setIsTitleKey(1);
                } else {
                    property.setIsTitleKey(0);
                }
                if (column.getColumnName().equals(param.getPrimaryKey())) {
                    property.setIsPrimaryKey(1);
                } else {
                    property.setIsPrimaryKey(0);
                }
                property.setStatus(1);
                property.setUniqueIdentifier(UUID.randomUUID().toString());
                return property;
            }).collect(Collectors.toList());
            ontologyPropertyService.batchAdd(collect);
        }
        return count;
    }

    @Override
    public Integer delete(String uniqueIdentifier) {
        return ontologyMetaMapper.deleteByUniqueIdentifier(uniqueIdentifier);
    }

    @Override
    public Integer update(OntologyMetaBO ontologyMetaBO) {
        if (ontologyMetaBO.getId() == null) {
            throw new RuntimeException("id必传");
        }
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByPrimaryKey(ontologyMetaBO.getId());
        BeanUtils.copyProperties(ontologyMetaBO, ontologyMeta);
        ontologyMeta.setUpdateTime(new Date());

        int count = ontologyMetaMapper.updateByPrimaryKeySelective(ontologyMeta);
        return count;
    }

    @Override
    public OntologyMetaVO getOntologyById(Long id) {
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByPrimaryKey(id);
        OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
        BeanUtils.copyProperties(ontologyMeta, ontologyMetaVO);
        return ontologyMetaVO;
    }

    @Override
    public OntologyMetaVO getOntologyByApi(String api) {
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByApi(api);
        OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
        BeanUtils.copyProperties(ontologyMeta, ontologyMetaVO);
        return ontologyMetaVO;
    }

    @Override
    public OntologyMetaVO getOntologyByUniqueIdentifier(String uniqueIdentifier) {
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(uniqueIdentifier);
        OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
        BeanUtils.copyProperties(ontologyMeta, ontologyMetaVO);
        return ontologyMetaVO;
    }

    @Override
    public List<OntologyMetaVO> getAllOntologies() {
        List<OntologyMeta> result = ontologyMetaMapper.selectAllOntologies();
        List<OntologyMetaVO> retResult = new ArrayList();
        for (OntologyMeta meta : result) {
            OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
            BeanUtils.copyProperties(meta, ontologyMetaVO);
            retResult.add(ontologyMetaVO);
        }
        return retResult;
    }

    @Override
    public Integer getCountByStatus(int status) {
        int count = ontologyMetaMapper.getCountByStatus(status);
        return count;
    }

    @Override
    public List<OntologyMetaVO> searchOntologies(String keyword) {

        List<OntologyMeta> result = ontologyMetaMapper.searchOntologies(keyword);
        List<OntologyMetaVO> retResult = new ArrayList();
        for (OntologyMeta meta : result) {
            OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
            BeanUtils.copyProperties(meta, ontologyMetaVO);
            retResult.add(ontologyMetaVO);
        }
        return retResult;
    }

    @Override
    public PageInfo<OntologyGroupMetaVO> searchGroupOntologies(String keyword, Integer page, Integer size) {

        PageHelper.startPage(page, size);
        PageInfo<OntologyGroup> pageInfo = new PageInfo<>(ontologyGroupMapper.selectAll());
        List<OntologyGroupMetaVO> collect = pageInfo.getList().stream().map(group -> {
            OntologyGroupMetaVO ontologyGroupMetaVO = new OntologyGroupMetaVO();
            ontologyGroupMetaVO.setGroupId(group.getGroupId());
            ontologyGroupMetaVO.setGroupName(group.getGroupName());
            List<OntologyMetaVO> ontologyMetaVOS = listOntologiesByGroup(group.getGroupId());
            ontologyGroupMetaVO.setOntologyCount(ontologyMetaVOS.size());
            ontologyGroupMetaVO.setMetaVOS(ontologyMetaVOS);
            return ontologyGroupMetaVO;
        }).collect(Collectors.toList());
        PageInfo<OntologyGroupMetaVO> pageResult = new PageInfo<>(collect);
        BeanUtils.copyProperties(pageInfo, pageResult);
        pageResult.setList(collect);
        return pageResult;
    }

    @Override
    public List<OntologyMetaVO> listOntologiesByGroup(String groupId) {

        List<OntologyMeta> result = ontologyMetaMapper.listOntologiesByGroup(groupId);
        List<OntologyMetaVO> retResult = new ArrayList();
        for (OntologyMeta meta : result) {
            OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
            BeanUtils.copyProperties(meta, ontologyMetaVO);
            retResult.add(ontologyMetaVO);
        }
        return retResult;
    }
}
