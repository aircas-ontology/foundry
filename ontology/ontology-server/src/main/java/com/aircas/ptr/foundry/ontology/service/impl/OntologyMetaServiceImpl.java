package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.constant.Visibility;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.ontology.client.EntityClient;
import com.aircas.ptr.foundry.ontology.common.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.converter.ClientParamConverter;
import com.aircas.ptr.foundry.ontology.converter.ParamToEntityConverter;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.model.param.EntityNodeParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
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
public class OntologyMetaServiceImpl extends ServiceImpl<OntologyMetaMapper, OntologyMeta> implements OntologyMetaService {

    private final OntologyMetaMapper ontologyMetaMapper;

    private final OntologyPropertyService ontologyPropertyService;

    private final TableMetadataService tableMetadataService;

    private final OntologyLinkGroupService linkService;

    private final FunctionService functionService;

    private final FunctionParamService functionParamService;

    private final OntologyActionService actionService;

    private final ActionHandleRuleService actionHandleRuleService;

    private final ActionHandleTaskService actionHandleTaskService;

    private final ObjectService objectService;

    private final EntityService entityService;

    private final EntityClient entityClient;


    @Override
    @Transactional(value = "mainTransactionManager")
    public String createOntology(OntologyCreateParam ontologyCreateParam) {
        /**
         *
         * 1 创建元数据
         * 2 创建属性、关系、函数、行为（关系、函数、行为只在本体继承场景）
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
                this.save(meta);
                break;
            case INHERIT:
                createOntologyByInherit(ontologyCreateParam, meta);
                break;
            case DATASOURCE:
                createOntologyByDatasource(ontologyCreateParam, meta);
                break;
        }
        return meta.getUniqueIdentifier();
    }

    private void createOntologyByInherit(OntologyCreateParam ontologyCreateParam, OntologyMeta meta) {
        var childIdentifer = meta.getUniqueIdentifier();
        var parentIdentifer = ontologyCreateParam.getParentOntologyUniqueIdentifier();
        //校验父本体
        var parentOntology = this.getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, parentIdentifer));
        PreconditionUtils.checkArgument(parentOntology != null, "父本体不存在", ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
        //创建子本体元数据
        meta.setBackingDatasourceId(parentOntology.getBackingDatasourceId()).setOtherDatasourceId(parentOntology.getOtherDatasourceId());
        this.save(meta);
        // 创建属性
        var parentProperties = ontologyPropertyService.list(new LambdaUpdateWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyCreateParam.getParentOntologyUniqueIdentifier()));
        var childProps = parentProperties.stream().map(v -> OntologyProperty.builder()
                .ontologyUniqueIdentifier(childIdentifer)
                .visibility(v.getVisibility())
                .associateDatasourceColumnName(v.getAssociateDatasourceColumnName())
                .isAssociateKey(v.getIsAssociateKey())
                .apiName(v.getApiName())
                .datasourceColumnName(v.getDatasourceColumnName())
                .datasourceId(v.getDatasourceId())
                .description(v.getDescription())
                .displayName(v.getDisplayName())
                .isPrimaryKey(v.getIsPrimaryKey())
                .isTitleKey(v.getIsTitleKey())
                .propertyType(v.getPropertyType())
                .status(v.getStatus())
                .uniqueIdentifier(IdGenerator.generateUUID())
                .build())
                .collect(Collectors.toList());
        ontologyPropertyService.saveBatch(childProps);
        // 创建关系
        var parentLinks = linkService.list(new LambdaUpdateWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, parentIdentifer)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, parentIdentifer));
        if (CollectionUtils.isNotEmpty(parentLinks)) {
            var childLinks = parentLinks.stream().map(v -> {
                var link = OntologyLinkGroup.builder()
                        .mapping(v.getMapping())
                        .name(v.getName())
                        .ontologyUniqueIdentifierFrom(v.getOntologyUniqueIdentifierFrom())
                        .ontologyUniqueIdentifierTo(v.getOntologyUniqueIdentifierTo())
                        .propertyUniqueIdentifierFrom(v.getPropertyUniqueIdentifierFrom())
                        .propertyUniqueIdentifierTo(v.getPropertyUniqueIdentifierTo())
                        .status(v.getStatus())
                        .uniqueIdentifier(IdGenerator.generateUUID())
                        .build();
                if (v.getOntologyUniqueIdentifierFrom().equals(parentIdentifer)) {
                    link.setOntologyUniqueIdentifierFrom(childIdentifer).setPropertyUniqueIdentifierFrom(findChildOntologyProperty(parentProperties, childProps, v.getPropertyUniqueIdentifierFrom()));
                } else {
                    link.setOntologyUniqueIdentifierTo(childIdentifer).setPropertyUniqueIdentifierTo(findChildOntologyProperty(parentProperties, childProps, v.getPropertyUniqueIdentifierTo()));
                }
                return link;
            }).collect(Collectors.toList());
            linkService.saveBatch(childLinks);
        }
        // 函数
        var funcViews = functionService.queryFunctionViewByOntologyId(parentIdentifer);
        List<Function> functions = Lists.newArrayList();
        List<FunctionParamPO> functionParams = Lists.newArrayList();

        funcViews.forEach(view -> {
            var funcId = SnowflakeIdUtil.get();
            functions.add(Function.builder()
                    .code(view.getCode())
                    .api(view.getApi())
                    .description(view.getDescription())
                    .objectTypes(childIdentifer)
                    .status(view.getStatus())
                    .id(funcId).build());
            functionParams.addAll(view.getFunctionParams().stream().map(v -> FunctionParamPO.builder().functionId(funcId)
                    .description(v.getDescription())
                    .parameterName(v.getParameterName())
                    .parameterType(v.getParameterType())
                    .build()).collect(Collectors.toList()));
        });
        functionService.saveBatch(functions);
        functionParamService.saveBatch(functionParams);
        // 行为
        var actionViews = actionService.queryActionViewByOntologyIdentifier(parentIdentifer);
        List<OntologyAction> actions = Lists.newArrayList();
        List<ActionHandleRule> rules = Lists.newArrayList();
        List<ActionHandleTask> tasks = Lists.newArrayList();
        List<OntologyActionMappingIn> mappingIns = Lists.newArrayList();

        actionViews.forEach(action -> {
            var actionId = SnowflakeIdUtil.get();
            actions.add(OntologyAction.builder().api(action.getApi())
                    .description(action.getDescription())
                    .displayName(action.getDisplayName())
                    .functionApi(action.getFunctionApi())
                    .handleType(action.getHandleType())
                    .ontologyLinkGroupId(action.getOntologyLinkGroupId())
                    .status(action.getStatus())
                    .id(actionId)
                    .ontologyUniqueIdentifier(childIdentifer)
                    .build());
            mappingIns.addAll(action.getMappingIn().stream().map(v -> OntologyActionMappingIn.builder()
                    .propertyUniqueIdentifier(findChildOntologyProperty(parentProperties, childProps, v.getPropertyUniqueIdentifier()))
                    .parameterName(v.getParameterName())
                    .ontologyActionId(actionId)
                    .build()).collect(Collectors.toList()));

            if (action.getRules() != null) {
                rules.add(ActionHandleRule.builder()
                        .status(action.getRuleStatus())
                        .actionId(actionId)
                        .objectPrimaryKey(action.getRuleObjectPrimaryKey())
                        .ruleConnectType(action.getRuleConnectType())
                        .rules(action.getRules())
                        .build());
            }
            if (action.getCorn() != null) {
                tasks.add(ActionHandleTask.builder()
                        .status(action.getTaskStatus())
                        .actionId(actionId)
                        .corn(action.getCorn())
                        .startTime(action.getStartTime())
                        .endTime(action.getEndTime())
                        .objectPrimaryKey(action.getTaskObjectPrimaryKey())
                        .build());
            }
        });
        actionService.saveBatch(actions);
        actionHandleRuleService.saveBatch(rules);
        actionHandleTaskService.saveBatch(tasks);

        // 远程调用创建实体

    }

    private String findChildOntologyProperty(List<OntologyProperty> parentProperties, List<OntologyProperty> childProps, String targetUniqId) {
        var apiName = parentProperties.stream()
                .filter(p -> p.getUniqueIdentifier().equals(targetUniqId))
                .findFirst().get().getApiName();
        return childProps.stream().filter(p -> p.getApiName().equals(apiName)).findFirst().get().getUniqueIdentifier();
    }


    private void createOntologyByDatasource(OntologyCreateParam ontologyCreateParam, OntologyMeta meta) {
        //1 创建本体元数据
        var primaryDataSource = ontologyCreateParam.getPrimaryDataSource();
        PreconditionUtils.checkArgument(primaryDataSource != null && CollectionUtils.isNotEmpty(primaryDataSource.getColumnParamList()), "primaryDataSource is null");
        var primaryTableName = primaryDataSource.getColumnParamList().get(0).getDatasourceId();
        meta.setBackingDatasourceId(primaryTableName);
        var associateDataSources = ontologyCreateParam.getAssociateDataSources();
        if (CollectionUtils.isNotEmpty(associateDataSources)) {
            var dataSources = associateDataSources.stream().map(ds -> ds.getColumnParamList().get(0).getDatasourceId()).collect(Collectors.toList());
            meta.setOtherDatasourceId(String.join(",", dataSources));
        }
        //2 创建本体属性
        // 主数据源属性
        var properties = primaryDataSource.getColumnParamList().stream()
                .map(v -> ParamToEntityConverter.convert(v, meta.getUniqueIdentifier()))
                .collect(Collectors.toList());
        //  其他数据源属性
        if (CollectionUtils.isNotEmpty(associateDataSources)) {
            //校验关联健
            associateDataSources.stream().forEach(ds -> {
                var associateKey = ds.getColumnParamList().stream().filter(v -> v.getIsAssociateKey()).findFirst().orElse(null);
                PreconditionUtils.checkArgument(associateKey != null &&
                        properties.stream().anyMatch(v -> v.getDatasourceColumnName().equals(associateKey.getAssociateDatasourceColumnName())), "找不到关联健或者关联的属性错误");
            });
            //生成属性表数据
            var otherProps = associateDataSources.stream()
                    .flatMap(ds -> ds.getColumnParamList().stream().map(v -> {
                        var prop = ParamToEntityConverter.convert(v, meta.getUniqueIdentifier());
                        if (v.getIsPrimaryKey() || v.getIsAssociateKey()) {
                            return prop.setVisibility(Visibility.HIDDEN.getValue());
                        }
                        return prop;
                    }))
                    .collect(Collectors.toList());
            properties.addAll(otherProps);
        }
        //校验property apiName是否有冲突
        PreconditionUtils.checkArgument(properties.stream().map(v -> StringUtils.lowerCase(v.getApiName())).collect(Collectors.toSet()).size() == properties.size(), "apiName存在冲突");
        //校验titleKey
        var titleKeyExist = properties.stream().filter(v -> v.getIsTitleKey() == 1).count();
        PreconditionUtils.checkArgument(titleKeyExist == 1, "名称健不存在或多个");
        //批量插入
        ontologyPropertyService.saveBatch(properties);
        //创建实体表、实体数据和实体节点
        entityClient.createTableAndEntities(EntityCreateParam.builder()
                .primaryDataSource(ClientParamConverter.convert(primaryDataSource, ontologyCreateParam.getApiName()))
                .associateDataSources(CollectionUtils.isNotEmpty(associateDataSources) ?
                        associateDataSources.stream()
                                .map(v -> ClientParamConverter.convert(v, ontologyCreateParam.getApiName() + "_" + v.getColumnParamList().get(0).getDatasourceId()))
                                .collect(Collectors.toList())
                        : null)
                .build());
    }


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
