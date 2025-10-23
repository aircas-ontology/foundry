package com.aircas.ptr.foundry.ontology.entity.service.impl;

import com.aircas.ptr.foundry.ontology.common.vo.EntityRelationVO;
import com.aircas.ptr.foundry.ontology.entity.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.entity.repository.arangodb.EntityNodeRepository;
import com.aircas.ptr.foundry.ontology.entity.repository.arangodb.EntityRelationRepository;
import com.aircas.ptr.foundry.ontology.entity.repository.mapper.main.EntityTableMapper;
import com.aircas.ptr.foundry.ontology.entity.service.EntityNodeService;
import com.aircas.ptr.foundry.ontology.entity.service.EntityTableService;
import lombok.var;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EntityNodeServiceImpl implements EntityNodeService {

    @Resource
    private EntityNodeRepository nodeRepository;

    @Resource
    private EntityRelationRepository relationRepository;


    @Override
    public void createEntityRelation(String entityFrom, String entityTo, String relationType) {

        var fromNodes = nodeRepository.findByTableName(entityFrom);
        var toNodes = nodeRepository.findByTableName(entityTo);

        var relations = new ArrayList<EntityRelation>();
        fromNodes.forEach(from ->
                toNodes.forEach(to ->
                        relations.add(EntityRelation.builder()
                                .from(from)
                                .to(to)
                                .isDeleted(false)
                                .createTime(new Date())
                                .updateTime(new Date())
                                .type(relationType).build())
                )
        );
        relationRepository.batchSave(relations);

    }

    @Override
    public List<EntityRelationVO> queryRelations(String tableName, Object primaryKeyValue) {
        var relations = relationRepository.queryRelationByTableNameAndPrimaryKey(tableName,primaryKeyValue);

        return relations.stream().map(v-> EntityRelationVO.builder()
                .type(v.getType())
                .nodeNameFrom(v.getFrom().getDisplayName())
                .nodeNameTo(v.getTo().getDisplayName())
                .nodeIdFrom(v.getFrom().getId())
                .nodeIdTo(v.getTo().getId())
                .build()).collect(Collectors.toList());
    }
}
