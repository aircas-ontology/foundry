package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.constant.QuerySortEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.ontology.common.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class OntologyMetaServiceImpl extends ServiceImpl<OntologyMetaMapper, OntologyMeta> implements OntologyMetaService {

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private OntologyPropertyService ontologyPropertyService;

    @Resource
    private OntologyActionMappingInService actionMappingInService;

    @Resource
    private OntologyLinkGroupService linkService;

    @Resource
    private OntologyActionService actionService;

    @Resource
    private OntologyGroupService groupService;

    @Resource
    private EntityService entityService;

    @Resource
    private FileService fileService;

    @Override
    public void updateIcon(MultipartFile image, String uniqueIdentifier) {
        try {
            var iconUrl = fileService.getThumbnailByImage(image);
            ontologyMetaMapper.update(new OntologyMeta().setIcon(iconUrl), new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, uniqueIdentifier));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new BusinessException("updateIcon failed");
        }

    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public String createOntology(OntologyMetaCreateParam ontologyCreateParam) {
        var meta = OntologyMeta.builder()
                .uniqueIdentifier(IdGenerator.generateUUID())
                .apiName(ontologyCreateParam.getApiName())
                .description(ontologyCreateParam.getDescription())
                .displayName(ontologyCreateParam.getDisplayName())
                .status(Status.ENABLE.getValue())
                .icon(ontologyCreateParam.getIconUrl())
                .build();
        //自主创建
        if (StringUtils.isEmpty(ontologyCreateParam.getParentOntologyUniqueIdentifier())) {
            meta.setMetaGroupId(String.join(",", ontologyCreateParam.getGroupIds()));
            this.save(meta);
        }//继承创建
        else {
            createOntologyByInherit(ontologyCreateParam, meta);
        }
        return meta.getUniqueIdentifier();
    }


    private void createOntologyByInherit(OntologyMetaCreateParam ontologyCreateParam, OntologyMeta meta) {
        var childIdentifier = meta.getUniqueIdentifier();
        var parentIdentifier = ontologyCreateParam.getParentOntologyUniqueIdentifier();
        //校验父本体
        var parentOntology = this.getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, parentIdentifier));
        PreconditionUtils.checkArgument(parentOntology != null, "父本体不存在", ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
        //创建子本体元数据
        meta.setMetaGroupId(parentOntology.getMetaGroupId())
                .setParentUniqueIdentifier(parentIdentifier)
                .setStatus(parentOntology.getStatus());
        this.save(meta);
        // 创建属性
        var parentProperties = ontologyPropertyService.list(new LambdaUpdateWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyCreateParam.getParentOntologyUniqueIdentifier()));
        var childProps = parentProperties.stream().map(v -> OntologyProperty.builder()
                .ontologyUniqueIdentifier(childIdentifier)
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
                .tag(v.getTag())
                .build())
                .collect(Collectors.toList());
        ontologyPropertyService.saveBatch(childProps);
        // 创建关系
        var parentLinks = linkService.list(new LambdaUpdateWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, parentIdentifier)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, parentIdentifier));
        List<OntologyLinkGroup> childLinks = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(parentLinks)) {
            childLinks = parentLinks.stream().map(v -> {
                var link = OntologyLinkGroup.builder()
                        .name(v.getName())
                        .ontologyUniqueIdentifierFrom(v.getOntologyUniqueIdentifierFrom())
                        .ontologyUniqueIdentifierTo(v.getOntologyUniqueIdentifierTo())
                        .status(v.getStatus())
                        .uniqueIdentifier(IdGenerator.generateUUID())
                        .build();
                return link;
            }).collect(Collectors.toList());
            linkService.saveBatch(childLinks);
        }
        // 创建行为（不创建行为调度）
        var actionViews = actionService.queryActionViewByOntologyIdentifier(parentIdentifier);
        List<OntologyAction> actions = Lists.newArrayList();
        List<OntologyActionMappingIn> mappingIns = Lists.newArrayList();

        actionViews.forEach(action -> {
            var actionId = SnowflakeIdUtil.get();
            actions.add(OntologyAction.builder().api(ontologyCreateParam.getApiName() + "_" + action.getActionApi())
                    .description(action.getDescription())
                    .displayName(action.getDisplayName())
                    .functionApi(action.getFunctionApi())
                    .ontologyLinkGroupId(action.getOntologyLinkGroupId())
                    .status(action.getStatus())
                    .id(actionId)
                    .ontologyUniqueIdentifier(childIdentifier)
                    .build());
            mappingIns.addAll(action.getMappingIn().stream().map(v -> OntologyActionMappingIn.builder()
                    .propertyUniqueIdentifier(findChildOntologyProperty(parentProperties, childProps, v.getPropertyUniqueIdentifier()))
                    .parameterName(v.getParameterName())
                    .ontologyActionId(actionId)
                    .build()).collect(Collectors.toList()));
        });

        actionService.saveBatch(actions);
        actionMappingInService.saveBatch(mappingIns);

        // 创建实体节点和实体关系
        var primaryKey = parentProperties.stream().filter(v -> v.getIsPrimaryKey() == 1).findFirst();
        var titleKey = parentProperties.stream().filter(v -> v.getIsTitleKey() == 1).findFirst();

        if (primaryKey.isPresent() && StringUtils.isNotEmpty(primaryKey.get().getDatasourceColumnName())) {
            //标题健需要和主键为同一个数据源
            var titleColumn = "";
            if (titleKey != null && StringUtils.equals(titleKey.get().getDatasourceId(), primaryKey.get().getDatasourceId())) {
                titleColumn = titleKey.get().getDatasourceColumnName();
            }
            entityService.createNodes(meta.getUniqueIdentifier(), primaryKey.get().getDatasourceId(), primaryKey.get().getDatasourceColumnName(), titleColumn);
            childLinks.stream().forEach(link -> entityService.createEntityRelations(link));
        }
    }


    private String findChildOntologyProperty(List<OntologyProperty> parentProperties, List<OntologyProperty> childProps, String targetUniqId) {
        var apiName = parentProperties.stream()
                .filter(p -> p.getUniqueIdentifier().equals(targetUniqId))
                .findFirst().get().getApiName();
        return childProps.stream().filter(p -> p.getApiName().equals(apiName)).findFirst().get().getUniqueIdentifier();
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
    @Transactional(value = "mainTransactionManager")
    public void deleteOntology(String ontologyIdentifier) {
        var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyIdentifier));
        PreconditionUtils.checkArgument(meta != null, "ontology not exist:" + ontologyIdentifier, ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
        //删除本体元数据
        this.remove(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, ontologyIdentifier));
        //删除属性
        ontologyPropertyService.remove(new LambdaQueryWrapper<OntologyProperty>().eq(OntologyProperty::getOntologyUniqueIdentifier, ontologyIdentifier));
        //删除关系
        linkService.remove(new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyIdentifier).or()
                .eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyIdentifier));
        //删除行为，参数，规则，任务 todo 停止本体下定时调度任务
        actionService.removeByOntologyIdentifier(ontologyIdentifier);
        //删除所有实体表、节点和边
        entityService.deleteNodesAndRelationsByOntologyId(meta.getApiName());
    }


    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateMeta(OntologyUpdateParam updateParam) {
        var meta = getOne(new LambdaQueryWrapper<OntologyMeta>().eq(OntologyMeta::getUniqueIdentifier, updateParam.getOntologyIdentifier()));
        var updateWrapper = new LambdaUpdateWrapper<OntologyMeta>()
                .set(OntologyMeta::getIcon, updateParam.getIcon())
                .set(OntologyMeta::getDescription, updateParam.getDescription())
                .set(OntologyMeta::getDisplayName, updateParam.getDisplayName())
                .set(OntologyMeta::getMetaGroupId, String.join(",", updateParam.getGroupIds()));
        update(meta, updateWrapper);
    }


    @Override
    public OntologyMetaInfoVO getMetaByUniqueIdentifier(String uniqueIdentifier) {
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByUniqueIdentifier(uniqueIdentifier);
        return DataConverter.convert(ontologyMeta);
    }


    @Override
    public List<OntologyMetaInfoVO> searchByKeyword(String keyword) {
        var searchKeyword = StringUtils.isEmpty(keyword) ? "" : keyword;
        return ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().like(OntologyMeta::getDisplayName, searchKeyword)
                .or().like(OntologyMeta::getDescription, keyword)
                .or().like(OntologyMeta::getApiName, keyword))
                .stream().map(DataConverter::convert).collect(Collectors.toList());
    }

    @Override
    public List<OntologyMetaNodeVO> getOntologyTreeByByGroupId(String groupId) {
        List<String> groups = StringUtils.isEmpty(groupId)
                ? groupService.list(new QueryWrapper<>()).stream().map(v -> v.getGroupId()).collect(Collectors.toList())
                : Lists.newArrayList(groupId);

        List<OntologyMetaNodeVO> res = Lists.newArrayList();
        for (var gid : groups) {
            var metaMap = list(new LambdaQueryWrapper<OntologyMeta>().like(OntologyMeta::getMetaGroupId, gid))
                    .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> OntologyMetaNodeVO.builder()
                            .parentUniqueIdentifier(v.getParentUniqueIdentifier())
                            .uniqueIdentifier(v.getUniqueIdentifier())
                            .displayName(v.getDisplayName())
                            .childNodes(new ArrayList<>())
                            .build()));
            res.addAll(metaMap.values().stream().filter(v -> StringUtils.isEmpty(v.getParentUniqueIdentifier())).map(parent ->
                    {
                        buildTree(parent, metaMap);
                        return parent;
                    }
            ).collect(Collectors.toList()));
        }
        return res;
    }

    private void buildTree(OntologyMetaNodeVO parent, Map<String, OntologyMetaNodeVO> metaMap) {
        metaMap.values().forEach(child -> {
            if (StringUtils.equals(child.getParentUniqueIdentifier(), parent.getUniqueIdentifier())) {
                parent.getChildNodes().add(child);
                buildTree(child, metaMap);
            }
        });
    }


    @Override
    public List<OntologyGroupMetaVO> getByGroupId(String groupId, OntologyOrderByEnum orderBy, QuerySortEnum sort) {
        List<OntologyGroup> groups = Lists.newArrayList();
        if (StringUtils.isEmpty(groupId)) {
            groups.addAll(groupService.list());
        } else {
            groups.add(groupService.getOne(new LambdaQueryWrapper<OntologyGroup>().eq(OntologyGroup::getGroupId, groupId)));
        }

        var metaList = list(new QueryWrapper<OntologyMeta>().orderBy(orderBy != null, sort != null && sort.equals(QuerySortEnum.ASC), orderBy == null ? "id" : orderBy.getValue()))
                .stream().map(meta -> DataConverter.convert(meta)).collect(Collectors.toList());
        return groups.stream().map(group -> {
            var metaInfoVOList = metaList.stream().filter(meta -> meta.getMetaGroupId().contains(group.getGroupId())).collect(Collectors.toList());
            return OntologyGroupMetaVO.builder()
                    .groupId(group.getGroupId())
                    .groupName(group.getGroupName())
                    .metaVOS(metaInfoVOList)
                    .build();
        }).collect(Collectors.toList());
    }
}
