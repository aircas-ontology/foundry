package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.client.EntityClient;
import com.aircas.ptr.foundry.ontology.client.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.converter.ClientParamConverter;
import com.aircas.ptr.foundry.ontology.converter.ParamToEntityConverter;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.model.param.EntityNodeParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.*;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
//import java.util.Map;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/11 16:15
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyMetaServiceImpl implements OntologyMetaService {

    private final OntologyMetaMapper ontologyMetaMapper;

    private final OntologyPropertyService ontologyPropertyService;


    private final TableMetadataService tableMetadataService;

    private final OntologyGroupMapper ontologyGroupMapper;

    private final ObjectService objectService;

    private final EntityService entityService;

    private final EntityClient entityClient;


    @Override
    @Transactional(value = "mainTransactionManager")
    public String createOntology(OntologyCreateParam ontologyCreateParam) {
        /**
         *
         * 1 创建元数据
         * 2 创建属性、关系、函数、行为
         * 3 创建实体
         */
        var meta = OntologyMeta.builder()
                .uniqueIdentifier(IdGenerator.generateUUID())
                .apiName(ontologyCreateParam.getApiName())
                .description(ontologyCreateParam.getDescription())
                .displayName(ontologyCreateParam.getDisplayName())
                .status(Status.ENABLE.getValue())
                .icon(ontologyCreateParam.getIcon())
                .parentUniqueIdentifier(ontologyCreateParam.getParentOntologyUniqueIdentifier())
                .metaGroupId(String.join(",", ontologyCreateParam.getGroupIds()))
                .build();

        switch (ontologyCreateParam.getCreateMode()) {
            case NONE:
                ontologyMetaMapper.insert(meta);
                break;
            case INHERIT:

                break;
            case DATASOURCE:
                //1 创建本体元数据
                var primaryDataSource = ontologyCreateParam.getPrimaryDataSource();
                PreconditionUtils.checkArgument(primaryDataSource != null && CollectionUtils.isNotEmpty(primaryDataSource.getColumnParamList()), "primaryDataSource is null");
                var primaryTableName = primaryDataSource.getColumnParamList().get(0).getTableName();
                meta.setBackingDatasourceId(primaryTableName);
                var associateDataSources = ontologyCreateParam.getAssociateDataSources();
                if (CollectionUtils.isNotEmpty(associateDataSources)) {
                    var dataSources = associateDataSources.stream().map(ds -> ds.getColumnParamList().get(0).getTableName()).collect(Collectors.toList());
                    meta.setOtherDatasourceId(String.join(",", dataSources));
                }
                //2 创建本体属性
                // 主数据源属性
                var properties = primaryDataSource.getColumnParamList().stream()
                        .map(ParamToEntityConverter::convert)
                        .collect(Collectors.toList());
                //  其他数据源属性
                if (CollectionUtils.isNotEmpty(associateDataSources)) {
                    //校验关联健
                    associateDataSources.stream().forEach(ds -> {
                        var associateKey = ds.getColumnParamList().stream().filter(v -> v.getIsAssociateKey()).findFirst().orElse(null);
                        PreconditionUtils.checkArgument(associateKey != null &&
                                properties.stream().anyMatch(v -> v.getDatasourceColumnName().equals(associateKey.getPrimaryDataSourceKey())), "找不到关联健或者关联的属性错误");
                    });
                    //生成属性表数据
                    var otherProps = associateDataSources.stream()
                            .flatMap(ds -> ds.getColumnParamList().stream().filter(v -> !v.getIsPrimaryKey() && !v.getIsAssociateKey()))
                            .collect(Collectors.toList());
                    properties.addAll(otherProps.stream().map(ParamToEntityConverter::convert).collect(Collectors.toList()));
                }
                //校验property apiName是否有冲突
                PreconditionUtils.checkArgument(properties.stream()
                        .map(v -> v.getApiName()).collect(Collectors.toSet()).size() == properties.size(), "apiName存在冲突");
                //校验titleKey
                var titleKeyExist = properties.stream().filter(v -> v.getIsTitleKey() == 1).count();
                PreconditionUtils.checkArgument(titleKeyExist == 1, "名称健不存在或多个");
                //批量插入
                ontologyPropertyService.saveBatch(properties);
                //创建实体表、实体数据和实体节点
                entityClient.createTableAndEntities(EntityCreateParam.builder()
                        .primaryDataSource(ClientParamConverter.convert(primaryDataSource))
                        .associateDataSources(CollectionUtils.isNotEmpty(associateDataSources) ?
                                associateDataSources.stream().map(ClientParamConverter::convert).collect(Collectors.toList()) : null)
                        .build());
                break;

        }
        return meta.getUniqueIdentifier();
    }


//    @Override
//    public OntologyMetaVO add(OntologyMetaAddParam param) {
//        int count = ontologyMetaMapper.selectByDisplayName(param.getDisplayName());
//        if (count != 0) {
//            throw new DuplicatedDataException("本体名称已存在");
//        }
//        // 进行本体插入
//        OntologyMeta ontologyMeta = new OntologyMeta();
//        BeanUtils.copyProperties(param, ontologyMeta);
//        ontologyMeta.setUniqueIdentifier(UUID.randomUUID().toString());
//        ontologyMeta.setMetaGroupId(String.join(",", param.getMetaGroupId()));
//        count = ontologyMetaMapper.insertSelective(ontologyMeta);
//        // 如果datasource不为空，插入本体属性
//        if (param.getIsMapAllParam()) {
//            creatAllProperties(param.getBackingDatasourceId(), ontologyMeta.getUniqueIdentifier(), param.getTitleKey(), param.getPrimaryKey());
//        }
//        // 如果本体需要继承
//        if (param.getParentUniqueIdentifier() != null &&
//                !param.getParentUniqueIdentifier().isEmpty() &&
//                param.getParentComponents() != null &&
//                !param.getParentComponents().isEmpty()) {
//            String parentUniqueIdentifier = param.getParentUniqueIdentifier();
//            List<OntologyComponentEnum> parentComponents = param.getParentComponents();
//            for (OntologyComponentEnum parent : parentComponents) {
//                switch (parent) {
//                    case LINK:
//                    case ACTION:
//                    case FUNCTION:
//                        // TODO: 补充连接、动作、函数、模型等继承
//                        break;
//                    case PROPERTY:
//                        List<OntologyPropertyVO> ontologyPropertyVOS = ontologyPropertyService.selectByOntologyUniqueIdentifier(parentUniqueIdentifier);
//                        List<OntologyPropertyBO> collect = ontologyPropertyVOS.stream().map(pro -> {
//                            OntologyPropertyBO ontologyPropertyBO = new OntologyPropertyBO();
//                            BeanUtils.copyProperties(pro, ontologyPropertyBO);
//                            ontologyPropertyBO.setUniqueIdentifier(null);
//                            Date now = new Date();
//                            ontologyPropertyBO.setCreateTime(now);
//                            ontologyPropertyBO.setUpdateTime(now);
//                            ontologyPropertyBO.setOntologyUniqueIdentifier(ontologyMeta.getUniqueIdentifier());
//                            ontologyPropertyBO.setDatasourceId(null);
//                            ontologyPropertyBO.setDatasourceColumnName(null);
//                            return ontologyPropertyBO;
//                        }).collect(Collectors.toList());
//                        ontologyPropertyService.batchAdd(collect);
//                        break;
//                }
//            }
//        }
//
//        if (param.getBackingDatasourceId() != null) {
//            // 插入实体数据\创建node节点
//            int num = batchInsertEntity(ontologyMeta.getUniqueIdentifier(), ontologyMeta.getApiName(), ontologyMeta.getDisplayName());
//            log.info("插入实体数据{}条", num);
//        }
//
//        OntologyMetaVO ontologyMetaVO = new OntologyMetaVO();
//        OntologyMeta resMeta = ontologyMetaMapper.selectByUniqueIdentifier(ontologyMeta.getUniqueIdentifier());
//        BeanUtils.copyProperties(resMeta, ontologyMetaVO);
//        return ontologyMetaVO;
//    }

    private int batchInsertEntity(String uniqueIdentifier, String apiName, String displayName) {

        List<OntologyPropertyVO> propertyVOS = ontologyPropertyService.selectByOntologyApi(apiName);
        String key = Objects.requireNonNull(propertyVOS.stream().filter(prop -> prop.getIsPrimaryKey() == 1).findFirst().orElse(null)).getApiName();
        String title = Objects.requireNonNull(propertyVOS.stream().filter(prop -> prop.getIsTitleKey() == 1).findFirst().orElse(null)).getApiName();

        int pageNum = 1, count = 0;
        while (true) {
            PageInfo<Map<String, Object>> mapPageInfo = objectService.queryObjectList(uniqueIdentifier, pageNum, 200);
            if (mapPageInfo == null || mapPageInfo.getList() == null || mapPageInfo.getList().isEmpty()) {
                break;
            }
            // 插入实体数据
            Integer num = entityService.batchInsertEntityTable(apiName, mapPageInfo.getList());
            // 创建节点
            List<EntityNodeParam> collect = mapPageInfo.getList().stream().map(item -> new EntityNodeParam(
                    uniqueIdentifier + "@" + item.get(key),
                    (String) item.get(title),
                    "",
                    apiName,
                    displayName
            )).collect(Collectors.toList());
            if (!entityService.createEntityNode(collect)) {
                log.warn("创建实体失败");
            }
            pageNum++;
            count += num;
        }
        return count;
    }

    @Override
    public List<OntologyMetaVO> selectByUniqueIdentifiers(List<String> uniqueIdentifiers) {
        if (uniqueIdentifiers.isEmpty()) {
            return new ArrayList<>();
        }
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
            property.setPropertyType(OntologyDataTypeEnum.valueOfPg(column.getType()));
            property.setDatasourceId(backingDatasourceId);
            property.setDescription(column.getDescription());
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
    public void deleteOntology(String uniqueIdentifier) {
        /**
         * todo:
         * 1 删除本体元数据
         * 2 删除属性
         * 3 删除关系
         * 4 删除函数
         * 5 删除行为
         * 6 删除所有实体
         */
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
    public void updateMeta(OntologyUpdateParam updateParam) {

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
    public OntologyMetaInfoVO getMetaByUniqueIdentifier(String uniqueIdentifier) {
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(uniqueIdentifier);
        return OntologyMetaInfoVO.builder()
                .uniqueIdentifier(ontologyMeta.getUniqueIdentifier())
                .apiName(ontologyMeta.getApiName())
                .createTime(ontologyMeta.getCreateTime())
                .updateTime(ontologyMeta.getUpdateTime())
                .description(ontologyMeta.getDescription())
                .icon(ontologyMeta.getIcon())
                .metaGroupId(Arrays.stream(ontologyMeta.getMetaGroupId().split(",")).collect(Collectors.toList()))
                .displayName(ontologyMeta.getDisplayName())
                .build();
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
    public List<OntologyMetaInfoVO> searchByKeyword(String keyword) {
        return ontologyMetaMapper.searchByKeyword(keyword).stream().map(v -> {
            return OntologyMetaInfoVO.builder()
                    .uniqueIdentifier(v.getUniqueIdentifier())
                    .apiName(v.getApiName())
                    .createTime(v.getCreateTime())
                    .updateTime(v.getUpdateTime())
                    .description(v.getDescription())
                    .icon(v.getIcon())
                    .metaGroupId(Arrays.stream(v.getMetaGroupId().split(",")).collect(Collectors.toList()))
                    .displayName(v.getDisplayName())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<OntologyGroupMetaVO> getByGroupId(String groupId) {
        return null;
    }

//    @Override
//    public PageInfo<OntologyGroupMetaVO> searchGroupOntologies(String keyword, Integer page, Integer size) {
//
//        PageHelper.startPage(page, size);
//        PageInfo<OntologyGroup> pageInfo = new PageInfo<>(ontologyGroupMapper.selectAll());
//        List<OntologyGroupMetaVO> collect = pageInfo.getList().stream().map(group -> {
//            OntologyGroupMetaVO ontologyGroupMetaVO = new OntologyGroupMetaVO();
//            ontologyGroupMetaVO.setGroupId(group.getGroupId());
//            ontologyGroupMetaVO.setGroupName(group.getGroupName());
//            List<OntologyMetaVO> ontologyMetaVOS = listOntologiesByGroup(group.getGroupId());
//            ontologyGroupMetaVO.setOntologyCount(ontologyMetaVOS.size());
//            ontologyGroupMetaVO.setMetaVOS(ontologyMetaVOS);
//            return ontologyGroupMetaVO;
//        }).collect(Collectors.toList());
//        PageInfo<OntologyGroupMetaVO> pageResult = new PageInfo<>(collect);
//        BeanUtils.copyProperties(pageInfo, pageResult);
//        pageResult.setList(collect);
//        return pageResult;
//    }

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
