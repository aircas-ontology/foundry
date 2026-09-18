package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkDirectionEnum;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyActionLink;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionLinkMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkCategoryMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;



@Service
@Slf4j
public class OntologyLinkGroupServiceImpl extends ServiceImpl<OntologyLinkGroupMapper, OntologyLinkGroup> implements OntologyLinkGroupService {

    @Resource
    private OntologyActionLinkMapper actionLinkMapper;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private OntologyLinkCategoryMapper ontologyLinkCategoryMapper;

    @Resource
    private EntityService entityService;

    @Override
    @Transactional(value = "mainTransactionManager")
    public void createLink(OntologyLinkCreateParam linkCreateParam) {
        // 校验关系分类必填且存在
        var categoryId = linkCreateParam.getCategoryId();
        PreconditionUtils.checkArgument(categoryId != null, "categoryId is empty", HttpStatus.BAD_REQUEST);
        var category = ontologyLinkCategoryMapper.selectById(categoryId);
        PreconditionUtils.checkArgument(category != null, "关系分类不存在：" + categoryId, HttpStatus.BAD_REQUEST);

        // 入参未携带 spaceId，需通过两端的本体对象反查其所属空间id
        var fromMeta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, linkCreateParam.getOntologyUniqueIdentifierFrom())
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkArgument(fromMeta != null,
                "开始本体不存在：" + linkCreateParam.getOntologyUniqueIdentifierFrom(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(fromMeta.getOntologySpaceId() != null,
                "开始本体未归属任何空间，无法创建关系", HttpStatus.BAD_REQUEST);

        var toMeta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, linkCreateParam.getOntologyUniqueIdentifierTo())
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        PreconditionUtils.checkArgument(toMeta != null,
                "结束本体不存在：" + linkCreateParam.getOntologyUniqueIdentifierTo(), HttpStatus.BAD_REQUEST);
        PreconditionUtils.checkArgument(fromMeta.getOntologySpaceId().equals(toMeta.getOntologySpaceId()),
                "开始本体与结束本体不属于同一空间，无法创建关系", HttpStatus.BAD_REQUEST);

        var link = OntologyLinkGroup.builder()
                .uniqueIdentifier(IdGenerator.generateUUID())
                .status(Status.ENABLE.getValue())
                .ontologyUniqueIdentifierFrom(linkCreateParam.getOntologyUniqueIdentifierFrom())
                .ontologyUniqueIdentifierTo(linkCreateParam.getOntologyUniqueIdentifierTo())
                .name(linkCreateParam.getName())
                .type(linkCreateParam.getType())
                .categoryId(linkCreateParam.getCategoryId())
                .ontologySpaceId(fromMeta.getOntologySpaceId())
                .build();
        //创建本体间关系
        save(link);
        //创建实体间关系
        entityService.createEntityRelations(link.getUniqueIdentifier());
    }

    @Override
    public List<OntologyMetaInfoVO> getLinkedOntology(String ontologyUniqueIdentifier) {
        var links = list(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifier));
        if (CollectionUtils.isEmpty(links)) {
            return Lists.newArrayList();
        }
        var ontologyIds = links.stream().flatMap(v -> Stream.of(v.getOntologyUniqueIdentifierFrom(), v.getOntologyUniqueIdentifierTo())).collect(Collectors.toList());
        var metas = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, ontologyIds).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
        return metas.stream().map(DataConverter::convert).collect(Collectors.toList());
    }

    @Override
    public OntologyLinkInfoVO getLinkByUniqueIdentifier(String uniqueIdentifier) {
        var link = getOne(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, uniqueIdentifier));
        PreconditionUtils.checkArgument(link != null, "关系不存在：" + uniqueIdentifier);
        var ontologyIds = Lists.newArrayList(link.getOntologyUniqueIdentifierFrom(), link.getOntologyUniqueIdentifierTo());
        var metaMap = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, ontologyIds).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v));
        var from = metaMap.get(link.getOntologyUniqueIdentifierFrom());
        var to = metaMap.get(link.getOntologyUniqueIdentifierTo());
        return DataConverter.convert(link, from, to);
    }

    @Override
    public List<OntologyLinkInfoVO> getLinksByGroupId(String groupId) {
        if (StringUtils.isEmpty(groupId)) {
            var links = list(new LambdaQueryWrapper<>());
            if (CollectionUtils.isEmpty(links)) {
                return Lists.newArrayList();
            }
            return buildLinkInfo(links);
        }

        var ids = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().like(OntologyMeta::getMetaGroupId, groupId).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream().map(v -> v.getUniqueIdentifier()).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(ids)) {
            return Lists.newArrayList();
        }
        var links = list(new LambdaQueryWrapper<OntologyLinkGroup>().in(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ids)
                .in(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ids));

        if (CollectionUtils.isEmpty(links)) {
            return Lists.newArrayList();
        }
        return buildLinkInfo(links);
    }

    @Override
    public List<OntologyLinkInfoVO> getByCategoryId(Integer categoryId) {
        log.info("[getByCategoryId] received categoryId={}", categoryId);
        var queryWrapper = new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue());
        if (categoryId != null) {
            queryWrapper.eq(OntologyLinkGroup::getCategoryId, categoryId);
        }
        var links = list(queryWrapper);
        if (CollectionUtils.isEmpty(links)) {
            return Lists.newArrayList();
        }
        return buildLinkInfo(links);
    }

    @Override
    public OntologyLinkGraphVO getLinkGraph(Integer spaceId, String ontologyUniqueIdentifier) {
        PreconditionUtils.checkArgument(spaceId != null, "spaceId is empty", HttpStatus.BAD_REQUEST);
        if (StringUtils.isNotEmpty(ontologyUniqueIdentifier)) {
            var meta = ontologyMetaMapper.selectOne(new LambdaQueryWrapper<OntologyMeta>()
                    .eq(OntologyMeta::getUniqueIdentifier, ontologyUniqueIdentifier)
                    .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()));
            PreconditionUtils.checkArgument(meta != null, "无效的本体id", HttpStatus.BAD_REQUEST);
            PreconditionUtils.checkArgument(meta.getOntologySpaceId() != null && meta.getOntologySpaceId().equals(spaceId),
                    "本体不属于该空间", HttpStatus.BAD_REQUEST);
        }

        // 限定在本空间内；传了对象 id 则进一步只取以该对象为端点的关系，否则取空间全部关系
        var queryWrapper = new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue())
                .eq(OntologyLinkGroup::getOntologySpaceId, spaceId);
        if (StringUtils.isNotEmpty(ontologyUniqueIdentifier)) {
            queryWrapper.and(w -> w.eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier)
                    .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifier));
        }
        var links = list(queryWrapper);
        if (CollectionUtils.isEmpty(links)) {
            return OntologyLinkGraphVO.builder()
                    .centerUniqueIdentifier(ontologyUniqueIdentifier)
                    .nodes(Lists.newArrayList())
                    .edges(Lists.newArrayList())
                    .build();
        }

        // 收集边两端的本体id，查出节点信息（限制在本空间内）
        var idList = links.stream()
                .flatMap(l -> Stream.of(l.getOntologyUniqueIdentifierFrom(), l.getOntologyUniqueIdentifierTo()))
                .distinct()
                .collect(Collectors.toList());
        var nodes = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>()
                        .in(OntologyMeta::getUniqueIdentifier, idList)
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                        .eq(OntologyMeta::getOntologySpaceId, spaceId))
                .stream()
                .map(m -> OntologyLinkGraphVO.GraphNode.builder()
                        .uniqueIdentifier(m.getUniqueIdentifier())
                        .displayName(m.getDisplayName())
                        .apiName(m.getApiName())
                        .icon(m.getIcon())
                        .build())
                .collect(Collectors.toList());

        var edges = links.stream()
                .map(l -> OntologyLinkGraphVO.GraphEdge.builder()
                        .uniqueIdentifier(l.getUniqueIdentifier())
                        .name(l.getName())
                        .type(l.getType())
                        .from(l.getOntologyUniqueIdentifierFrom())
                        .to(l.getOntologyUniqueIdentifierTo())
                        .categoryId(l.getCategoryId())
                        .build())
                .collect(Collectors.toList());

        return OntologyLinkGraphVO.builder()
                .centerUniqueIdentifier(ontologyUniqueIdentifier)
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    @Override
    public List<OntologyLinkInfoVO> getLinksByOntologyUniqueIdentifier(String ontologyUniqueIdentifier, OntologyLinkDirectionEnum direction) {
        List<OntologyLinkGroup> links = Lists.newArrayList();
        switch (direction) {
            case ALL:
                links = list(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier)
                        .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifier));
                break;
            case FROM:
                links = list(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier));
                break;
            case TO:
                links = list(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifier));
                break;
        }

        if (CollectionUtils.isEmpty(links)) {
            return Lists.newArrayList();
        }
        return buildLinkInfo(links);
    }


    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteLinkByLinkUniqueIdentifier(String linkUniqIdentifier) {
        var query = new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getUniqueIdentifier, linkUniqIdentifier);
        var link = getOne(query);
        PreconditionUtils.checkArgument(link != null, "无效的关系id", HttpStatus.BAD_REQUEST);
        // 检测是否被行为使用到
        var actions = actionLinkMapper.selectList(new LambdaQueryWrapper<OntologyActionLink>().eq(OntologyActionLink::getOntologyLinkUniqueIdentifier, linkUniqIdentifier));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(actions), "该关系被本体的某个行为使用到，不能删除", HttpStatus.FORBIDDEN);
        // 删除本体关系
        remove(query);
        // 删除实体关系
        entityService.deleteRelationsByLinkId(linkUniqIdentifier);
    }


    private List<OntologyLinkInfoVO> buildLinkInfo(List<OntologyLinkGroup> links) {
        var idList = links.stream().flatMap(l -> Stream.of(l.getOntologyUniqueIdentifierFrom(), l.getOntologyUniqueIdentifierTo())).collect(Collectors.toList());

        var metaMap = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, idList).eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v));

        return links.stream().map(link -> {
            var from = metaMap.get(link.getOntologyUniqueIdentifierFrom());
            var to = metaMap.get(link.getOntologyUniqueIdentifierTo());
            return DataConverter.convert(link, from, to);
        }).collect(Collectors.toList());
    }
}
