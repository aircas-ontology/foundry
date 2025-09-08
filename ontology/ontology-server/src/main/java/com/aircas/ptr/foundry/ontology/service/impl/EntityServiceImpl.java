package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.common.util.StringUtil;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.model.request.EntityNodeParam;
import com.aircas.ptr.foundry.ontology.model.request.EntityTableFieldParam;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @className: EntityServiceImpl
 * @author: yangj
 * @date: 2025/4/8 18:40
 * @version: 1.0
 * @description:
 */
@Slf4j
@Service
public class EntityServiceImpl implements EntityService {

    @Value("${entity.table-url.create}")
    private String tableCreate;

    @Value("${entity.table-url.delete}")
    private String tableDelete;

    @Value("${entity.table-url.exist}")
    private String tableExist;

    @Value("${entity.table-url.count}")
    private String tableCount;

    @Value("${entity.table-url.batch}")
    private String tableBatchInsert;

    @Value("${entity.node-url.create}")
    private String nodeCreate;

    @Value("${entity.node-url.delete}")
    private String nodeDelete;

    @Value("${entity.node-url.exist}")
    private String nodeExist;

    @Value("${entity.node-url.batch}")
    private String nodeBatch;

    /**
     * 创建实体表。
     *
     * @param tableName    表名
     * @param tableComment 表注释
     * @param fields       字段列表
     * @return 如果创建成功，则返回 true；否则返回 false
     */
    @Override
    public Boolean createEntityTable(String tableName, String tableComment, List<EntityTableFieldParam> fields) {

        Map<String, Object> params = new HashMap<>();
        params.put("tableName", tableName);
        params.put("tableComment", tableComment);
        params.put("fields", fields);
        String resp = HttpUtil.doPost(tableCreate, params);
        log.info("创建数据库表{}，参数{}，结果{}", tableName, params, resp);
        return resp == null || resp.isEmpty();
    }

    /**
     * 删除实体表。
     *
     * @param tableName 表名
     * @return 如果删除成功，则返回 true；否则返回 false
     */
    @Override
    public Boolean deleteEntityTable(String tableName) {

        return HttpUtil.doDelete(tableDelete.replace("{table_name}", tableName));
    }

    /**
     * 检查实体表是否存在。
     *
     * @param tableName 表名
     * @return 如果存在则返回 true；否则返回 false
     */
    @Override
    public Boolean existsEntityTable(String tableName) {

        String resp = HttpUtil.doGet(tableExist.replace("{table_name}", tableName));
        return Objects.equals(resp, "true");
    }

    /**
     * 计算实体表中的记录数。
     *
     * @param tableName 表名
     * @return 实体表中的记录数
     */
    @Override
    public Integer countEntityTable(String tableName) {
        String resp = HttpUtil.doGet(tableCount.replace("{table_name}", tableName));
        return Integer.parseInt(resp);
    }

    /**
     * 批量插入实体数据到指定的表中。
     *
     * @param tableName 表名
     * @param entities  实体列表，每个实体是一个Map对象，键为字段名，值为字段值
     * @return 成功插入的记录数
     */
    @Override
    public Integer batchInsertEntityTable(String tableName, List<Map<String, Object>> entities) {

        String url = tableBatchInsert.replace("{table_name}", tableName);
        String s = HttpUtil.doPost(url, entities);
        log.info("批量数据数据{}插入结果{}", JSON.toJSONString(entities), s);
        return Integer.parseInt(s);
    }

    /**
     * 创建实体节点。
     *
     * @param id          节点ID
     * @param name        节点名称
     * @param description 节点描述
     * @param category    节点分类
     * @return 如果创建成功，则返回 true；否则返回 false
     */
    @Override
    public Boolean createEntityNode(String id, String name, String description, String category, String type) {

        String existResp = HttpUtil.doGet(nodeExist);
        if (StringUtil.isNotEmpty(existResp) && StringUtil.isNotBlank(existResp)) {
            if (!HttpUtil.doDelete(nodeDelete)) {
                return false;
            }
        }
        EntityNodeParam params = new EntityNodeParam(id, name, description, category, type);
        String resp = HttpUtil.doPost(nodeCreate, params);
        log.info("创建实体{}返回值：{}", JSON.toJSONString(name), resp);
        return true;
    }

    /**
     * 批量创建实体节点。
     *
     * @param nodes 实体节点列表
     * @return 如果创建成功，则返回 true；否则返回 false
     */
    @Override
    public Boolean createEntityNode(List<EntityNodeParam> nodes) {

        String resp = HttpUtil.doPost(nodeBatch, nodes);
        log.info("创建实体list，参数{}，返回值：{}", JSON.toJSONString(nodes), resp);
        return true;
    }

    /**
     * 删除实体节点。
     *
     * @param nodeId 节点ID
     * @return 如果删除成功，则返回 true；否则返回 false
     */
    @Override
    public Boolean deleteEntityNode(String nodeId) {
        return HttpUtil.doDelete(nodeDelete.replace("{node_id}", nodeId));
    }

    /**
     * 检查实体节点是否存在。
     *
     * @param nodeId 节点ID
     * @return 如果存在则返回 true；否则返回 false
     */
    @Override
    public Boolean existsEntityNode(String nodeId) {
        String resp = HttpUtil.doGet(nodeExist.replace("{node_id}", nodeId));
        return resp != null && StringUtil.isAllEmpty(resp);
    }
}
