package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.param.EntityActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.vo.EntityActionVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityLinkPropertyVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyDetailVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * @interfaceName: EntityService
 * @author: yangj
 * @date: 2025/4/8 18:26
 * @version: 1.0
 * @description: 实体数据操作，外部接口
 */
public interface EntityService {

    void updateNodesDisplayName(String ontologyUniqueIdentifier, String datasourceId, String primaryKeyColumnName, String titleKeyColumnName);

    void createEntityRelations(OntologyLinkGroup link);

    void createNodes(String ontologyUniqueIdentifier, String datasourceId, String primaryKeyColumnName, String titleKeyColumnName);

    void deleteRelationsByLinkId(String linkId);

    void deleteNodesAndRelationsByOntologyId(String ontologyUniqueIdentifier);

    List<EntityLinkPropertyVO> getEntityLinksByPrimaryKey(String ontologyUniqueIdentifier, Object entityPrimaryKey);

    List<EntityPropertyDetailVO> getEntityDetail(String ontologyUniqueIdentifier, Object entityPrimaryKey);

    Page<EntityInfoVO> getEntities(String ontologyUniqueIdentifier, Integer pageNum, Integer pageSize);

    List<EntityNode> getByByOntologyUniqIdentifier(String ontologyIdentifier);

    String executeAction(EntityActionExecuteParam param) throws Exception;
}
