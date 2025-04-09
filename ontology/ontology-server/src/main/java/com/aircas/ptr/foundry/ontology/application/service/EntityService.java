package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.repository.param.EntityTableFieldParam;

import java.util.List;

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
}
