package com.aircas.ptr.foundry.ontology.service;

import java.util.Map;

/**
 * 数据湖（entity_datasource 库）DML CDC -> ontology_instance 索引同步服务。
 * <p>
 * schema/table 为变更行所在数据湖表的 schema 与表名；before/after 为整行数据（列名 -> 值）。
 */
public interface EntityInstanceCdcJobService {

    boolean handleCreateCdc(String schema, String table, Map<String, Object> after);

    boolean handleUpdateCdc(String schema, String table, Map<String, Object> before, Map<String, Object> after);

    boolean handleDeleteCdc(String schema, String table, Map<String, Object> before);
}
