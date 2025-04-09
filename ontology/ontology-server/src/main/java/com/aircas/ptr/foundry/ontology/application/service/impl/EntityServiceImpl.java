package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.application.service.EntityService;
import com.aircas.ptr.foundry.ontology.repository.param.EntityTableFieldParam;
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

    @Value("${entity-table-rul.create}")
    private String create;

    @Value("${entity-table-rul.delete}")
    private String delete;

    @Value("${entity-table-rul.exist}")
    private String exist;

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
        HttpUtil.doPost(create, params);
        return null;
    }

    /**
     * 删除实体表。
     *
     * @param tableName 表名
     * @return 如果删除成功，则返回 true；否则返回 false
     */
    @Override
    public Boolean deleteEntityTable(String tableName) {

        return HttpUtil.doDelete(delete.replace("{table_name}", tableName));
    }

    /**
     * 检查实体表是否存在。
     *
     * @param tableName 表名
     * @return 如果存在则返回 true；否则返回 false
     */
    @Override
    public Boolean existsEntityTable(String tableName) {

        String resp = HttpUtil.doGet(exist.replace("{table_name}", tableName));
        return !Objects.equals(resp, "true");
    }
}
