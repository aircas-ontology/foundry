package com.aircas.ptr.foundry.ontology.service;

import java.util.List;

/**
 * Debezium CDC 任务管理服务
 * 使用 Result<T> 统一封装返回结果
 */
public interface CdcTaskInitService {
    /**
     * 初始化所有 CDC 任务
     * 先检查是否已存在，存在则跳过，不存在则创建
     *
     */
    void initializeCdcTasks() ;

    /**
     * 获取所有已存在的连接器列表
     *
     * @return List<String> - 连接器名称列表
     */
    List<String> getConnectorList() ;

}
