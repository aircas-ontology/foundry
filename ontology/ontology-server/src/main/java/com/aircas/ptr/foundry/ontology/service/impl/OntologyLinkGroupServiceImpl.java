package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyLinkGroupMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyMetaMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
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
    private OntologyActionMapper actionMapper;

    @Resource
    private OntologyMetaMapper ontologyMetaMapper;

    @Resource
    private EntityService entityService;

    @Override
    @Transactional(value = "mainTransactionManager")
    public void createLink(OntologyLinkCreateParam linkCreateParam) {

        var link = OntologyLinkGroup.builder()
                .uniqueIdentifier(IdGenerator.generateUUID())
                .status(Status.ENABLE.getValue())
                .ontologyUniqueIdentifierFrom(linkCreateParam.getOntologyUniqueIdentifierFrom())
                .ontologyUniqueIdentifierTo(linkCreateParam.getOntologyUniqueIdentifierTo())
                .name(linkCreateParam.getName())
                .type(linkCreateParam.getType())
                .build();
        //创建本体间关系
        save(link);
        //创建实体间关系
        entityService.createEntityRelations(link);
    }

    @Override
    public List<OntologyMetaInfoVO> getLinkedOntology(String ontologyUniqueIdentifier) {
        var links = list(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifier));
        if (CollectionUtils.isEmpty(links)) {
            return Lists.newArrayList();
        }
        var ontologyIds = links.stream().flatMap(v -> Stream.of(v.getOntologyUniqueIdentifierFrom(), v.getOntologyUniqueIdentifierTo())).collect(Collectors.toList());
        var metas = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, ontologyIds));
        return metas.stream().map(DataConverter::convert).collect(Collectors.toList());
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

        var ids = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().like(OntologyMeta::getMetaGroupId, groupId))
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
    public List<OntologyLinkInfoVO> getLinksByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) {
        var links = list(new LambdaQueryWrapper<OntologyLinkGroup>().eq(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, ontologyUniqueIdentifier)
                .or().eq(OntologyLinkGroup::getOntologyUniqueIdentifierTo, ontologyUniqueIdentifier));

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
        var actions = actionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getOntologyLinkGroupId, linkUniqIdentifier));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(actions), "该关系被本体的某个行为使用到，不能删除", HttpStatus.FORBIDDEN);
        // 删除本体关系
        remove(query);
        // 删除实体关系
        entityService.deleteRelationsByLinkId(linkUniqIdentifier);
    }


    private List<OntologyLinkInfoVO> buildLinkInfo(List<OntologyLinkGroup> links) {
        var idList = links.stream().flatMap(l -> Stream.of(l.getOntologyUniqueIdentifierFrom(), l.getOntologyUniqueIdentifierTo())).collect(Collectors.toList());

        var metaMap = ontologyMetaMapper.selectList(new LambdaQueryWrapper<OntologyMeta>().in(OntologyMeta::getUniqueIdentifier, idList))
                .stream().collect(Collectors.toMap(v -> v.getUniqueIdentifier(), v -> v));

        return links.stream().map(link -> {
            var from = metaMap.get(link.getOntologyUniqueIdentifierFrom());
            var to = metaMap.get(link.getOntologyUniqueIdentifierTo());
            return OntologyLinkInfoVO.builder()
                    .name(link.getName())
                    .uniqueIdentifier(link.getUniqueIdentifier())
                    .ontologyUniqueIdentifierFrom(from.getUniqueIdentifier())
                    .ontologyUniqueIdentifierTo(to.getUniqueIdentifier())
                    .ontologyIconFrom(from.getIcon())
                    .ontologyIconTO(to.getIcon())
                    .ontologyNameFrom(from.getDisplayName())
                    .ontologyNameTo(to.getDisplayName())
                    .type(link.getType())
                    .build();
        }).collect(Collectors.toList());
    }
}
