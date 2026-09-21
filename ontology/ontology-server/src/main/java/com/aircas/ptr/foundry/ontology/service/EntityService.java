package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyInstancesExportDTO;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * @interfaceName: EntityService
 * @author: yangj
 * @date: 2025/4/8 18:26
 * @version: 1.0
 * @description: 实体数据操作，外部接口
 */
public interface EntityService {

    void createEntityRelations(String linkUniqueIdentifier);

    void syncNodes(String ontologyUniqueIdentifier, String schemaName, String datasourceId, String primaryKeyColumnName, String titleKeyColumnName);

    void deleteRelationsByLinkId(String linkId);

    void deleteNodesAndRelationsByOntologyId(String ontologyUniqueIdentifier);

    List<EntityLinkPropertyVO> getEntityLinksByPrimaryKey(String ontologyUniqueIdentifier, Object entityPrimaryKey);

    List<EntityPropertyDetailVO> getEntityDetail(String ontologyUniqueIdentifier, Object entityPrimaryKey);

    Page<EntityInfoVO> getEntities(String ontologyUniqueIdentifier, String propertyName, Object propertyValue, Integer pageNum, Integer pageSize, Boolean needFilterVisibility);

    List<EntityNode> getByByOntologyUniqIdentifier(String ontologyIdentifier);

    String executeAction(EntityActionExecuteParam param) throws Exception;

    void updateProperty(EntityUpdateParam param);

    void createEntityNodes(String ontologyIdentifier);

    List<EntityIdsQueryVO> getByEntityIds(List<EntityIdsQueryParam> params);

    List<EntityLinksVO> getAllLinksByEntityIds(List<EntityIdsQueryParam> param);

    EntityNode findOneByOntologyUniqIdentifier(String ontologyIdentifier);

    void updateEntityPropertyAndRelation(String jsonString, ActionContextInfoDTO actionContext);

    void completeEntityNodeAndRelations(String ontologyUniqueIdentifier, Map<String, Object> entityPropertyMap);

    void deleteEntityNodeAndRelations(String ontologyUniqueIdentifier, Object entityPrimaryKey);

    void generateEntities(EntityGenerateParam param);

    Page<List<EntityPropertyGenericQueryVO>> genericQuery(EntityPropertyGenericQueryParam param);

    EntityPropertyRowDetailVO getEntityPropertyRowDetail(EntityPropertyRowQueryParam param);

    List<Object> createEntities(EntityCreateParam params);

    void insertEntityProperty(EntityPropertyInsertParam param);

    void updateEntityProperty(EntityPropertyUpdateParam param);

    void deleteEntityProperty(EntityPropertyDeleteParam param);

    Integer countEntity(String datasourceSchema, String datasourceId);

    void updateEntityRelation(EntityRelationUpdateParam param);

    /**
     * 导出指定本体的全量实例数据（仅节点）。
     *
     * <p>数据来源为数据湖物理表：以主键属性所在主表全量行为骨架，
     * 按 {@code table_field_mapping} 将关联表属性内存 join 入节点。
     * 若本体未绑定数据源（无实体表），则返回空 nodes。</p>
     *
     * @param ontologyUniqueIdentifier 本体唯一标识
     * @return 实例导出结构，无实体表时 nodes 为空列表
     */
    OntologyInstancesExportDTO exportInstances(String ontologyUniqueIdentifier);
}
