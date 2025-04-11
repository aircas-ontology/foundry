package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.repository.param.EntityNodeParam;
import com.aircas.ptr.foundry.ontology.repository.param.EntityTableFieldParam;

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

    /**
     * 创建实体表。
     *
     * @param tableName 表名
     * @param tableComment 表注释
     * @param fields 字段列表
     * @return 如果创建成功，则返回 true；否则返回 false
     */
    Boolean createEntityTable(String tableName, String tableComment, List<EntityTableFieldParam> fields);

    /**
     * 删除实体表。
     *
     * @param tableName 表名
     * @return 如果删除成功，则返回 true；否则返回 false
     */
    Boolean deleteEntityTable(String tableName);

    /**
     * 检查实体表是否存在。
     *
     * @param tableName 表名
     * @return 如果存在则返回 true；否则返回 false
     */
    Boolean existsEntityTable(String tableName);

    /**
     * 统计实体表中的记录数。
     *
     * @param tableName 表名
     * @return 实体表中的记录数，如果表不存在则返回 0
     */
    Integer countEntityTable(String tableName);

    /**
     * 批量插入数据到实体表中。
     *
     * @param tableName 表名
     * @param entities 要插入的数据列表，每个元素是一个包含字段和对应值的 Map
     * @return 如果插入成功，则返回插入的记录数；否则返回 0
     */
    Integer batchInsertEntityTable(String tableName, List<Map<String, Object>> entities);

    /**
     * 创建单个实体节点。
     *
     * @param id 节点ID
     * @param name 节点名称
     * @param description 节点描述
     * @param category 节点类别
     * @return 如果创建成功，则返回 true；否则返回 false
     */
    Boolean createEntityNode(String id, String name, String description, String category, String type);

    /**
     * 批量创建实体节点。
     *
     * @param nodes 实体节点列表
     * @return 如果所有节点都创建成功，则返回 true；否则返回 false
     */
    Boolean createEntityNode(List<EntityNodeParam> nodes);

    /**
     * 删除单个实体节点。
     *
     * @param nodeId 节点ID
     * @return 如果删除成功，则返回 true；否则返回 false
     */
    Boolean deleteEntityNode(String nodeId);

    /**
     * 检查实体节点是否存在。
     *
     * @param nodeId 节点ID
     * @return 如果存在则返回 true；否则返回 false
     */
    Boolean existsEntityNode(String nodeId);
}
