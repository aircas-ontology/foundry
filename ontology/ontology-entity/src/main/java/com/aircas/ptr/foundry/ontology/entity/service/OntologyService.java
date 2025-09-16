package com.aircas.ptr.foundry.ontology.entity.service;

import com.aircas.ptr.foundry.ontology.entity.entity.OntologyNode;
import com.aircas.ptr.foundry.ontology.entity.entity.OntologyRelation;
import com.aircas.ptr.foundry.ontology.entity.exception.ResourceNotFoundException;
import com.aircas.ptr.foundry.ontology.entity.model.dto.BatchImportDTO;
import com.aircas.ptr.foundry.ontology.entity.repository.OntologyNodeRepository;
import com.aircas.ptr.foundry.ontology.entity.repository.OntologyRelationRepository;
import com.arangodb.ArangoCursor;
import com.arangodb.springframework.core.ArangoOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;


@Service
public class OntologyService {

    @Autowired
    private OntologyNodeRepository nodeRepository;

    @Autowired
    private OntologyRelationRepository relationRepository;

    @Autowired
    private ArangoOperations arangoOperations;


    private static final Logger log = LoggerFactory.getLogger(OntologyService.class);


    @Transactional
    public OntologyNode createNode(OntologyNode node) {
        // 设置默认值
        if (node.getCreateTime() == null) {
            node.setCreateTime(new Date());
        }
        node.setUpdateTime(new Date());
        if (node.getVersion() == null) {
            node.setVersion(1);
        }
        if (node.getIsDeleted() == null) {
            node.setIsDeleted(false);
        }
        if (node.getStatus() == null) {
            node.setStatus("active");
        }
        return nodeRepository.save(node);
    }

    @Transactional
    public OntologyNode updateNode(String id, OntologyNode node) {
        OntologyNode existingNode = getNodeById(id);
        if (existingNode == null) {
            throw new RuntimeException("节点不存在: " + id);
        }

        // 更新基本信息
        existingNode.setName(node.getName());
        existingNode.setDescription(node.getDescription());
        existingNode.setType(node.getType());
        existingNode.setCategory(node.getCategory());
        existingNode.setProperties(node.getProperties());
        existingNode.setSource(node.getSource());
        existingNode.setStatus(node.getStatus());
        existingNode.setRemarks(node.getRemarks());

        // 更新元数据
        existingNode.setUpdateTime(new Date());
        existingNode.setUpdateBy(node.getUpdateBy());
        existingNode.setVersion(existingNode.getVersion() + 1);

        return nodeRepository.save(existingNode);
    }

    public OntologyNode getNodeById(String id) {
        return nodeRepository.findById(id).orElse(null);
    }

    public OntologyNode getNodeByName(String name) {
        return nodeRepository.findByName(name);
    }

    public List<OntologyNode> getAllNodes() {
        return (List<OntologyNode>) nodeRepository.findAll();
    }

    public void deleteNode(String id) {
        nodeRepository.deleteById(id);
    }

    // 关系相关方法


    public OntologyRelation createRelation(OntologyRelation relation) {
        // 验证并确保_from和_to字段格式正确
        if (relation.get_from() == null || relation.get_to() == null) {
            throw new IllegalArgumentException("关系的_from和_to字段不能为空");
        }

        // 确保_from和_to字段包含正确的集合前缀
        if (!relation.get_from().startsWith("nodes/") && !relation.get_from().contains("/")) {
            relation.set_from("nodes/" + relation.get_from());
        }

        if (!relation.get_to().startsWith("nodes/") && !relation.get_to().contains("/")) {
            relation.set_to("nodes/" + relation.get_to());
        }

        // 设置创建和更新时间（如果未设置）
        if (relation.getCreateTime() == null) {
            relation.setCreateTime(System.currentTimeMillis());
        }

        if (relation.getUpdateTime() == null) {
            relation.setUpdateTime(System.currentTimeMillis());
        }

        try {
            // 使用AQL直接插入关系文档
            String query = "INSERT @relation INTO relations RETURN NEW";
            Map<String, Object> bindVars = new HashMap<>();
            bindVars.put("relation", convertRelationToMap(relation));

            ArangoCursor<Map> cursor = arangoOperations.query(query, bindVars, null, Map.class);
            if (cursor.hasNext()) {
                Map result = cursor.next();
                // 将结果转换回OntologyRelation对象
                return mapToRelation(result);
            } else {
                throw new RuntimeException("创建关系失败");
            }
        } catch (Exception e) {
            log.error("创建关系时发生错误", e);
            throw new RuntimeException("创建关系失败: " + e.getMessage(), e);
        }
    }

    // 辅助方法：将OntologyRelation转换为Map
    private Map<String, Object> convertRelationToMap(OntologyRelation relation) {
        Map<String, Object> map = new HashMap<>();

        if (relation.get_key() != null) {
            map.put("_key", relation.get_key());
        }

        map.put("_from", relation.get_from());
        map.put("_to", relation.get_to());
        map.put("type", relation.getType());
        map.put("description", relation.getDescription());
        map.put("confidence", relation.getConfidence());
        map.put("weight", relation.getWeight());
        map.put("status", relation.getStatus());
        map.put("source", relation.getSource());
        map.put("createTime", relation.getCreateTime());
        map.put("updateTime", relation.getUpdateTime());
        map.put("properties", relation.getProperties());

        return map;
    }

    // 辅助方法：将Map转换为OntologyRelation
    private OntologyRelation mapToRelation(Map map) {
        OntologyRelation relation = new OntologyRelation();

        if (map.containsKey("_id")) {
            relation.set_id((String) map.get("_id"));
        }

        if (map.containsKey("_key")) {
            relation.set_key((String) map.get("_key"));
        }

        if (map.containsKey("_from")) {
            relation.set_from((String) map.get("_from"));
        }

        if (map.containsKey("_to")) {
            relation.set_to((String) map.get("_to"));
        }

        if (map.containsKey("type")) {
            relation.setType((String) map.get("type"));
        }

        if (map.containsKey("description")) {
            relation.setDescription((String) map.get("description"));
        }

        if (map.containsKey("confidence")) {
            Object confidence = map.get("confidence");
            if (confidence instanceof Number) {
                relation.setConfidence(((Number) confidence).doubleValue());
            }
        }

        if (map.containsKey("weight")) {
            Object weight = map.get("weight");
            if (weight instanceof Number) {
                relation.setWeight((double) ((Number) weight).intValue());
            }
        }

        if (map.containsKey("status")) {
            relation.setStatus((String) map.get("status"));
        }

        if (map.containsKey("source")) {
            relation.setSource((String) map.get("source"));
        }

        if (map.containsKey("createTime")) {
            Object createTime = map.get("createTime");
            if (createTime instanceof Number) {
                relation.setCreateTime(((Number) createTime).longValue());
            }
        }

        if (map.containsKey("updateTime")) {
            Object updateTime = map.get("updateTime");
            if (updateTime instanceof Number) {
                relation.setUpdateTime(((Number) updateTime).longValue());
            }
        }

        if (map.containsKey("properties")) {
            Object properties = map.get("properties");
            if (properties instanceof Map) {
                relation.setProperties((Map<String, Object>) properties);
            }
        }

        return relation;
    }

    public OntologyRelation getRelationById(String id) {
        return relationRepository.findById(id).orElse(null);
    }

    public List<OntologyRelation> getRelationsByFromNode(String fromNode) {
        return relationRepository.findBy_from(fromNode);
    }

    public List<OntologyRelation> getRelationsByToNode(String toNode) {
        return relationRepository.findBy_to(toNode);
    }

    public List<OntologyRelation> getRelationsByType(String relationType) {
        return relationRepository.findByType(relationType);
    }

    public void deleteRelation(String id) {
        relationRepository.deleteById(id);
    }

    @Transactional
    public void batchImport(BatchImportDTO batchData) {
        // 先导入所有节点
        Map<String, String> nodeNameToId = new HashMap<>();
        for (BatchImportDTO.NodeDTO nodeDTO : batchData.getNodes()) {
            OntologyNode node = new OntologyNode();
            node.setName(nodeDTO.getName());
            node.setDescription(nodeDTO.getDescription());
            OntologyNode savedNode = nodeRepository.save(node);
            nodeNameToId.put(node.getName(), savedNode.getId());
        }

        // 再导入所有关系
        for (BatchImportDTO.RelationDTO relationDTO : batchData.getRelations()) {
            String fromNodeId = nodeNameToId.get(relationDTO.getFromNodeName());
            String toNodeId = nodeNameToId.get(relationDTO.getToNodeName());
            if (fromNodeId != null && toNodeId != null) {
                OntologyRelation relation = new OntologyRelation();
                relation.set_from(fromNodeId);
                relation.set_to(toNodeId);
                relation.setType(relationDTO.getRelationType());
                relation.setDescription(relationDTO.getDescription());
                relationRepository.save(relation);
            }
        }
    }

    public List<OntologyNode> searchNodes(Map<String, Object> conditions) {
        ArangoCursor<OntologyNode> cursor = arangoOperations.query(buildSearchQuery(conditions), conditions, null, OntologyNode.class);
        return cursor.asListRemaining();
    }

    private String buildSearchQuery(Map<String, Object> conditions) {
        StringBuilder query = new StringBuilder("FOR node IN nodes FILTER 1==1 ");

        if (conditions.containsKey("name")) {
            query.append(" AND LIKE(LOWER(node.name), LOWER(@name), true) ");
        }
        if (conditions.containsKey("type")) {
            query.append(" AND node.type == @type ");
        }
        if (conditions.containsKey("category")) {
            query.append(" AND node.category == @category ");
        }
        if (conditions.containsKey("status")) {
            query.append(" AND node.status == @status ");
        }
        if (conditions.containsKey("createTimeStart")) {
            query.append(" AND node.createTime >= @createTimeStart ");
        }
        if (conditions.containsKey("createTimeEnd")) {
            query.append(" AND node.createTime <= @createTimeEnd ");
        }
        if (conditions.containsKey("isDeleted")) {
            query.append(" AND node.isDeleted == @isDeleted ");
        }

        query.append(" RETURN node");
        return query.toString();
    }

    public List<Map> getNodeWithRelations(String nodeId) {
        String query = "FOR node IN nodes " +
                "FILTER node._id == @nodeId " +
                "LET outRelations = (FOR v, e IN 1..1 OUTBOUND node relations RETURN {node: v, relation: e}) " +
                "LET inRelations = (FOR v, e IN 1..1 INBOUND node relations RETURN {node: v, relation: e}) " +
                "RETURN {node: node, outRelations: outRelations, inRelations: inRelations}";

        Map<String, Object> bindVars = new HashMap<>();
        bindVars.put("nodeId", "nodes/" + nodeId);

        return arangoOperations.query(query, bindVars, null, Map.class).asListRemaining();
    }

    /**
     * 获取节点及其活跃关系（active-time大于当前时间的关系）
     *
     * @param id 节点ID
     * @return 节点及其活跃关系列表
     */

    public Map<String, Object> getNodeWithActiveRelations(String id) {
        // 检查节点是否存在
        Optional<OntologyNode> nodeOptional = nodeRepository.findById(id);
        if (!nodeOptional.isPresent()) {
            throw new ResourceNotFoundException("节点不存在，ID: " + id);
        }

        OntologyNode node = nodeOptional.get();

        // 获取本地时区的当前时间
        ZonedDateTime currentTimeLocal = ZonedDateTime.now();
        String currentTimeLocalStr = currentTimeLocal.toString();

        log.info("本地当前时间: " + currentTimeLocalStr);

        // 准备返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("node", convertNodeToMap(node));
        result.put("inRelations", new ArrayList<>());
        result.put("outRelations", new ArrayList<>());

        try {
            // 获取关系集合名称
            String relationCollectionName = "relations"; // 请替换为实际的集合名称

            // 构建AQL查询，包含没有active-time的关系或active-time大于当前时间的关系
            // String query = "FOR r IN " + relationCollectionName + " " +
            //         "FILTER (r._from == @nodeId OR r._to == @nodeId) " +
            //         "FILTER r.properties == null OR " +
            //         "       r.properties.`active-time` == null OR " +
            //         "       r.properties.`active-time` == '' OR " +
            //         "       r.properties.`active-time` > @currentTimeLocal " +
            //         "LET sourceNode = DOCUMENT(r._from) " +
            //         "LET targetNode = DOCUMENT(r._to) " +
            //         "RETURN { " +
            //         "  source: sourceNode, " +
            //         "  relationship: r, " +
            //         "  target: targetNode, " +
            //         "  debug: { activeTime: r.properties != null ? r.properties.`active-time` : null, currentTimeLocal: @currentTimeLocal } " +
            //         "}";

            // 构建AQL查询，根据新的活跃关系判断逻辑
            String query = "FOR r IN " + relationCollectionName + " " +
                    "FILTER (r._from == @nodeId OR r._to == @nodeId) " +
                    "FILTER " +
                    "   r.properties == null OR " +
                    "   (" +
                    "     (r.properties.`active-start-time` == null OR r.properties.`active-start-time` == '' OR r.properties.`active-start-time` <= @currentTimeLocal) AND " +
                    "     (r.properties.`active-end-time` == null OR r.properties.`active-end-time` == '' OR r.properties.`active-end-time` >= @currentTimeLocal)" +
                    "   ) " +
                    "LET sourceNode = DOCUMENT(r._from) " +
                    "LET targetNode = DOCUMENT(r._to) " +
                    "RETURN { " +
                    "  source: sourceNode, " +
                    "  relationship: r, " +
                    "  target: targetNode, " +
                    "  debug: { " +
                    "    activeStartTime: r.properties != null ? r.properties.`active-start-time` : null, " +
                    "    activeEndTime: r.properties != null ? r.properties.`active-end-time` : null, " +
                    "    currentTimeLocal: @currentTimeLocal " +
                    "  } " +
                    "}";

            Map<String, Object> bindVars = new HashMap<>();
            bindVars.put("nodeId", "nodes/" + id);
            bindVars.put("currentTimeLocal", currentTimeLocalStr);

            log.info("查询参数 - 本地时间: " + currentTimeLocalStr);

            ArangoCursor<Map> cursor = arangoOperations.query(query, bindVars, null, Map.class);
            List<Map> relations = cursor.asListRemaining();

            log.info("查询返回的关系数量: " + relations.size());
            for (Map relation : relations) {
                Map debugInfo = (Map) relation.get("debug");
                log.info("调试信息: " + debugInfo);
            }

            // 分类关系为入边和出边
            List<Map<String, Object>> inRelations = new ArrayList<>();
            List<Map<String, Object>> outRelations = new ArrayList<>();

            for (Map relation : relations) {
                Map relationshipMap = (Map) relation.get("relationship");
                String fromId = (String) relationshipMap.get("_from");
                String toId = (String) relationshipMap.get("_to");

                Map<String, Object> relationData = new HashMap<>();
                relationData.put("source", relation.get("source"));
                relationData.put("relationship", relation.get("relationship"));
                relationData.put("target", relation.get("target"));

                // 判断是入边还是出边
                if (fromId.equals("nodes/" + id)) {
                    outRelations.add(relationData);
                } else {
                    inRelations.add(relationData);
                }
            }

            // 更新结果
            result.put("inRelations", inRelations);
            result.put("outRelations", outRelations);

            return result;
        } catch (Exception e) {
            log.error("查询节点活跃关系时发生错误", e);

            // 如果AQL查询失败，使用Java代码进行过滤
            try {
                Map<String, Object> manualResult = getNodeWithActiveRelationsManually(id, currentTimeLocal);
                if (manualResult != null) {
                    return manualResult;
                }
            } catch (Exception ex) {
                log.error("手动过滤活跃关系时发生错误", ex);
            }

            // 如果所有方法都失败，至少返回节点信息
            return result;
        }
    }

    // 手动过滤活跃关系的方法
    private Map<String, Object> getNodeWithActiveRelationsManually(String id, ZonedDateTime currentTime) {
        // 获取节点
        Optional<OntologyNode> nodeOptional = nodeRepository.findById(id);
        if (!nodeOptional.isPresent()) {
            throw new ResourceNotFoundException("节点不存在，ID: " + id);
        }

        OntologyNode node = nodeOptional.get();

        // 准备返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("node", convertNodeToMap(node));

        // 使用新的方法名称
        List<OntologyRelation> allRelations = relationRepository.findByFromOrTo("nodes/" + id, "nodes/" + id);

        // 分类关系为入边和出边
        List<Map<String, Object>> inRelations = new ArrayList<>();
        List<Map<String, Object>> outRelations = new ArrayList<>();

        // 遍历所有关系，筛选出活跃的关系
        for (OntologyRelation relation : allRelations) {
            Map<String, Object> properties = relation.getProperties();

            // 检查关系是否有properties字段且active-time存在且不为空
            boolean isActive = true; // 默认为活跃

            if (properties != null && properties.containsKey("active-time")) {
                String activeTimeStr = (String) properties.get("active-time");

                // 如果active-time为空字符串，视为永久活跃
                if (activeTimeStr != null && !activeTimeStr.isEmpty()) {
                    String currentTimeStr = currentTime.toString();

                    log.info("关系ID: " + relation.get_id() +
                            ", active-time: " + activeTimeStr +
                            ", 当前本地时间: " + currentTimeStr);

                    // 比较active-time和当前时间
                    isActive = activeTimeStr.compareTo(currentTimeStr) > 0;

                    if (!isActive) {
                        log.info("关系ID: " + relation.get_id() + " 的active-time (" + activeTimeStr +
                                ") 小于或等于当前本地时间 (" + currentTimeStr + ")，已过滤");
                    }
                }
            }

            if (isActive) {
                Map<String, Object> relationData = new HashMap<>();

                // 获取源节点和目标节点
                String fromId = relation.get_from();
                String toId = relation.get_to();

                // 从ID中提取纯节点ID（去掉"nodes/"前缀）
                String fromNodeId = fromId.replace("nodes/", "");
                String toNodeId = toId.replace("nodes/", "");

                Optional<OntologyNode> sourceNodeOpt = nodeRepository.findById(fromNodeId);
                Optional<OntologyNode> targetNodeOpt = nodeRepository.findById(toNodeId);

                if (sourceNodeOpt.isPresent() && targetNodeOpt.isPresent()) {
                    relationData.put("source", convertNodeToMap(sourceNodeOpt.get()));
                    relationData.put("relationship", convertRelationToMap(relation));
                    relationData.put("target", convertNodeToMap(targetNodeOpt.get()));

                    // 判断是入边还是出边
                    if (fromId.equals("nodes/" + id)) {
                        outRelations.add(relationData);
                    } else {
                        inRelations.add(relationData);
                    }
                }
            }
        }

        // 更新结果
        result.put("inRelations", inRelations);
        result.put("outRelations", outRelations);

        return result;
    }

    /**
     * 将节点对象转换为Map
     */
    private Map<String, Object> convertNodeToMap(OntologyNode node) {
        Map<String, Object> nodeMap = new HashMap<>();
        nodeMap.put("_id", node.getId());
        nodeMap.put("_key", node.getId().replace("nodes/", ""));
        nodeMap.put("name", node.getName());
        nodeMap.put("type", node.getType());
        nodeMap.put("properties", node.getProperties());
        return nodeMap;
    }

    /**
     * 查询某时间段内的节点及其活跃关系
     */
    public Map<String, Object> getActiveRelationsInTimespan(String startTimeStr, String endTimeStr, String nodeId) {
        try {
            // 解析时间字符串为ZonedDateTime
            ZonedDateTime startTime;
            ZonedDateTime endTime;

            try {
                startTime = ZonedDateTime.parse(startTimeStr);
                endTime = ZonedDateTime.parse(endTimeStr);
            } catch (Exception e) {
                throw new IllegalArgumentException("时间格式无效，请使用ISO-8601格式（如：2023-04-15T14:30:00+08:00[Asia/Shanghai]）");
            }

            log.info("查询时间段: {} 至 {}", startTime, endTime);

            // 准备返回结果
            Map<String, Object> result = new HashMap<>();

            // 获取关系集合名称
            String relationCollectionName = "relations";

            // 构建AQL查询
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("FOR r IN ").append(relationCollectionName).append(" ");

            // 如果指定了节点ID，则只查询与该节点相关的关系
            if (nodeId != null && !nodeId.isEmpty()) {
                queryBuilder.append("FILTER (r._from == @nodeId OR r._to == @nodeId) ");
            }

            // 添加时间段过滤条件
            queryBuilder.append("FILTER ");
            queryBuilder.append("  r.properties == null OR ");
            queryBuilder.append("  (r.properties.`active-start-time` == null AND r.properties.`active-end-time` == null) OR ");
            queryBuilder.append("  (");
            // 开始时间在查询时间段内
            queryBuilder.append("    (r.properties.`active-start-time` != null AND r.properties.`active-start-time` >= @startTime AND r.properties.`active-start-time` <= @endTime) OR ");
            // 结束时间在查询时间段内
            queryBuilder.append("    (r.properties.`active-end-time` != null AND r.properties.`active-end-time` >= @startTime AND r.properties.`active-end-time` <= @endTime) OR ");
            // 时间段包含查询时间段
            queryBuilder.append("    (r.properties.`active-start-time` != null AND r.properties.`active-end-time` != null AND ");
            queryBuilder.append("     r.properties.`active-start-time` <= @startTime AND r.properties.`active-end-time` >= @endTime) ");
            queryBuilder.append("  ) ");

            // 返回关系对象
            queryBuilder.append("RETURN { ");
            queryBuilder.append("  _id: r._id, ");
            queryBuilder.append("  _key: r._key, ");
            queryBuilder.append("  _from: r._from, ");
            queryBuilder.append("  _to: r._to, ");
            queryBuilder.append("  type: r.type, ");
            queryBuilder.append("  properties: r.properties ");
            queryBuilder.append("}");

            String query = queryBuilder.toString();
            log.info("执行查询: {}", query);

            // 设置查询参数
            Map<String, Object> bindVars = new HashMap<>();
            bindVars.put("startTime", startTimeStr);
            bindVars.put("endTime", endTimeStr);

            if (nodeId != null && !nodeId.isEmpty()) {
                bindVars.put("nodeId", "nodes/" + nodeId);
            }

            // 执行查询，使用Map类型接收结果
            ArangoCursor<Map> cursor = arangoOperations.query(query, bindVars, null, Map.class);
            List<Map> relations = cursor.asListRemaining();

            log.info("查询返回的关系数量: {}", relations.size());

            // 收集所有相关节点
            Set<String> nodeIds = new HashSet<>();
            List<Map<String, Object>> activeRelations = new ArrayList<>();

            for (Map relation : relations) {
                // 获取源节点和目标节点的ID
                String sourceId = (String) relation.get("_from");
                String targetId = (String) relation.get("_to");

                // 收集节点ID
                nodeIds.add(sourceId);
                nodeIds.add(targetId);

                // 获取源节点和目标节点
                OntologyNode sourceNode = nodeRepository.findById(sourceId.replace("nodes/", "")).orElse(null);
                OntologyNode targetNode = nodeRepository.findById(targetId.replace("nodes/", "")).orElse(null);

                // 创建关系数据
                Map<String, Object> relationData = new HashMap<>();
                relationData.put("relationship", relation);

                if (sourceNode != null) {
                    relationData.put("source", convertNodeToMap(sourceNode));
                }

                if (targetNode != null) {
                    relationData.put("target", convertNodeToMap(targetNode));
                }

                // 添加调试信息
                Map<String, Object> debug = new HashMap<>();
                Map<String, Object> properties = (Map<String, Object>) relation.get("properties");
                if (properties != null) {
                    debug.put("activeStartTime", properties.get("active-start-time"));
                    debug.put("activeEndTime", properties.get("active-end-time"));
                } else {
                    debug.put("activeStartTime", null);
                    debug.put("activeEndTime", null);
                }
                relationData.put("debug", debug);

                // 添加到活跃关系列表
                activeRelations.add(relationData);
            }

            // 如果指定了节点ID，则添加该节点
            if (nodeId != null && !nodeId.isEmpty()) {
                String fullNodeId = "nodes/" + nodeId;
                if (!nodeIds.contains(fullNodeId)) {
                    Optional<OntologyNode> nodeOptional = nodeRepository.findById(nodeId);
                    if (nodeOptional.isPresent()) {
                        nodeIds.add(fullNodeId);
                    }
                }
            }

            // 获取所有相关节点的详细信息
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (String id : nodeIds) {
                String nodeKey = id.replace("nodes/", "");
                Optional<OntologyNode> nodeOptional = nodeRepository.findById(nodeKey);
                if (nodeOptional.isPresent()) {
                    nodes.add(convertNodeToMap(nodeOptional.get()));
                }
            }

            // 构建返回结果
            Map<String, String> timespan = new HashMap<>();
            timespan.put("startTime", startTimeStr);
            timespan.put("endTime", endTimeStr);

            result.put("timespan", timespan);
            result.put("nodes", nodes);
            result.put("activeRelations", activeRelations);
            result.put("totalNodes", nodes.size());
            result.put("totalRelations", activeRelations.size());

            return result;

        } catch (Exception e) {
            log.error("查询时间段内的活跃关系时发生错误", e);
            throw new RuntimeException("查询时间段内的活跃关系时发生错误: " + e.getMessage(), e);
        }
    }
} 