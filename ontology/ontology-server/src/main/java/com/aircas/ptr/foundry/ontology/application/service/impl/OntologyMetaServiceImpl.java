package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyComponentEnum;
import com.aircas.ptr.foundry.common.exception.DuplicatedDataException;
import com.aircas.ptr.foundry.model.po.*;
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.application.service.TableMetadataService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.*;
import com.aircas.ptr.foundry.ontology.repository.param.OntologyMetaAddParam;
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
    public OntologyMetaVO add(OntologyMetaAddParam param) {
        int count = ontologyMetaMapper.selectByDisplayName(param.getDisplayName());
        if (count != 0) {
            throw new DuplicatedDataException("本体名称已存在");
        }
        // 进行本体插入
        OntologyMeta ontologyMeta = new OntologyMeta();
        BeanUtils.copyProperties(param, ontologyMeta);
        ontologyMeta.setUniqueIdentifier(UUID.randomUUID().toString());
        ontologyMeta.setMetaGroupId(param.getMetaGroupId().stream().collect(Collectors.joining(",")));
        count = ontologyMetaMapper.insertSelective(ontologyMeta);
        // 如果datasource不为空，插入本体属性
        if (param.getIsMapAllParam()) {
            creatAllProperties(param.getBackingDatasourceId(), ontologyMeta.getUniqueIdentifier(), param.getTitleKey(), param.getPrimaryKey());
        }
        // 如果本体需要继承
        if (param.getParentUniqueIdentifier() != null &&
                !param.getParentUniqueIdentifier().isEmpty() &&
                param.getParentComponents() != null &&
                param.getParentComponents().size() > 0) {
            String parentUniqueIdentifier = param.getParentUniqueIdentifier();
            List<OntologyComponentEnum> parentComponents = param.getParentComponents();
            for (OntologyComponentEnum parent : parentComponents) {
                switch (parent) {
                    case LINK:
                    case ACTION:
                    case FUNCTION:
                        // TODO: 补充连接、动作、函数、模型等继承
                        break;
                    case PROPERTY:
                        List<OntologyPropertyVO> ontologyPropertyVOS = ontologyPropertyService.selectByOntologyUniqueIdentifier(parentUniqueIdentifier);
                        List<OntologyPropertyBO> collect = ontologyPropertyVOS.stream().map(pro -> {
                            OntologyPropertyBO ontologyPropertyBO = new OntologyPropertyBO();
                            BeanUtils.copyProperties(pro, ontologyPropertyBO);
                            ontologyPropertyBO.setUniqueIdentifier(null);
                            Date now = new Date();
                            ontologyPropertyBO.setCreateTime(now);
                            ontologyPropertyBO.setUpdateTime(now);
                            ontologyPropertyBO.setOntologyUniqueIdentifier(ontologyMeta.getUniqueIdentifier());
                            ontologyPropertyBO.setDatasourceId(null);
                            ontologyPropertyBO.setDatasourceColumnName(null);
                            return ontologyPropertyBO;
                        }).collect(Collectors.toList());
                        ontologyPropertyService.batchAdd(collect);
                        break;
                }
            }
        }
        OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
        OntologyMeta resMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyMeta.getUniqueIdentifier());
        BeanUtils.copyProperties(resMeta, ontologyMetaVO);
        return ontologyMetaVO;
    }

    @Override
    public List<OntologyMetaVO> selectByUniqueIdentifiers(List<String> uniqueIdentifiers) {
        List<OntologyMeta> ontologyMetaList = ontologyMetaMapper.selectByUniqueIdentifiers(uniqueIdentifiers);
        List<OntologyMetaVO> ontologyMetaVOList = new ArrayList<>();
        for (OntologyMeta ontologyMeta : ontologyMetaList) {
            OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
            BeanUtils.copyProperties(ontologyMeta, ontologyMetaVO);
            ontologyMetaVOList.add(ontologyMetaVO);
        }
        return ontologyMetaVOList;
    }

    @Override
    public Integer countByGroup(String groupId) {

        return ontologyMetaMapper.sumByGroup(groupId);
    }

    /**
     * 插入所有的datasource字段作为本体属性
     *
     * @param backingDatasourceId
     * @param ontologyUniqueIdentifier
     * @param titleKey
     * @param primaryKey
     * @return
     */
    private Integer creatAllProperties(String backingDatasourceId, String ontologyUniqueIdentifier, String titleKey, String primaryKey) {

        List<TableColumnDescVO> columns = tableMetadataService.getColumns(backingDatasourceId);
        List<OntologyPropertyBO> collect = columns.stream().map(column -> {
            OntologyPropertyBO property = new OntologyPropertyBO();
            property.setOntologyUniqueIdentifier(ontologyUniqueIdentifier);
            property.setApiName(column.getColumnName());
            property.setDatasourceColumnName(column.getColumnName());
            property.setDatasourceId(backingDatasourceId);
            property.setDescription(column.getColumnName());
            property.setDisplayName(column.getDescription());
            if (column.getColumnName().equals(primaryKey)) {
                property.setIsPrimaryKey(1);
            } else {
                property.setIsPrimaryKey(0);
            }
            if (column.getColumnName().equals(titleKey)) {
                property.setIsTitleKey(1);
            } else {
                property.setIsTitleKey(0);
            }
            property.setStatus(1);
            return property;
        }).collect(Collectors.toList());
        return ontologyPropertyService.batchAdd(collect);
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
