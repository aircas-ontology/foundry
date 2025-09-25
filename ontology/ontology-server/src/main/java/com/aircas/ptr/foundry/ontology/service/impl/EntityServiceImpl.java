package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.common.util.StringUtil;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.model.param.EntityNodeParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityTableFieldParam;
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


    @Override
    public Boolean createEntityTable(String tableName, String tableComment, List<EntityTableFieldParam> fields) {
        return null;
    }

    @Override
    public Boolean deleteEntityTable(String tableName) {
        return null;
    }

    @Override
    public Boolean existsEntityTable(String tableName) {
        return null;
    }

    @Override
    public Integer countEntityTable(String tableName) {
        return null;
    }

    @Override
    public Integer batchInsertEntityTable(String tableName, List<Map<String, Object>> entities) {
        return null;
    }

    @Override
    public Boolean createEntityNode(String id, String name, String description, String category, String type) {
        return null;
    }

    @Override
    public Boolean createEntityNode(List<EntityNodeParam> nodes) {
        return null;
    }

    @Override
    public Boolean deleteEntityNode(String nodeId) {
        return null;
    }

    @Override
    public Boolean existsEntityNode(String nodeId) {
        return null;
    }
}
