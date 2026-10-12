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

    /**
     * 将指定关系下的全部实体关系边置为 ENABLE。
     * <p>平台 {@code createEntityRelations} 默认生成 Status.DELETE 的边（启用过滤查询看不到），
     * 子空间等一次性全量复制场景调用此方法将其激活为可见可用的关系。</p>
     *
     * @param linkUniqueIdentifier 关系唯一标识
     */
    void activateLinkRelations(String linkUniqueIdentifier);

    /**
     * 判断指定关系在 ArangoDB 中是否已存在 ENABLE 的实体关系边。
     * <p>用于子空间复制时镜像源关系的可见状态：源边为 ENABLE 则子空间新边也应 ENABLE，
     * 源边为 DELETE（或无边）则保持默认 DELETE，避免子空间关系与源不一致。</p>
     *
     * @param linkUniqueIdentifier 关系唯一标识
     * @return 是否存在 ENABLE 的关系边
     */
    boolean hasEnabledRelations(String linkUniqueIdentifier);

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

    /**
     * 导入指定本体的实例数据（若导出结构中带有 instances）。
     *
     * <p>将每个导出节点还原为实体行写入数据湖物理表：主键 id 由数据库 SERIAL 重新生成，
     * 丢弃导出携带的原 id；关联表（非 main 存储分组）属性按 List 下标还原为一对多多行。
     * 仅写数据湖，不建 ArangoDB 图节点。本体未绑定数据源或无有效节点时静默跳过。</p>
     *
     * @param ontologyUniqueIdentifier 本体唯一标识
     * @param instances                实例导出结构，为 null 或 nodes 为空时不做任何操作
     */
    void importInstances(String ontologyUniqueIdentifier, OntologyInstancesExportDTO instances);

    /**
     * 按本体空间 + 本体对象查询实例数据的指定属性值，支持可选过滤条件。
     * <p>过滤条件中的属性必须是该本体的属性，值类型需与属性定义的类型兼容，
     * 执行查询前先做完整校验，避免类型不匹配或 SQL 异常。</p>
     *
     * @param param 查询参数，包括 spaceId、ontologyUniqueIdentifier、propertyApiNames 及可选 filters
     * @return 分页结果
     */
    Page<List<EntityPropertyGenericQueryVO>> queryInstancePropertyData(EntityInstanceDataQueryParam param);
}
