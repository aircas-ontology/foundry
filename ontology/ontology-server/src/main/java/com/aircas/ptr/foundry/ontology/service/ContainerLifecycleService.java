package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;

import java.util.List;

/**
 * 容器算子生命周期服务：以「容器」为维度（containerKey 归组，一容器一实例）做启停/重启/状态刷新。
 * <p>常驻复用模型（设计 B）：一个容器算子只起一次，其全部接口算子复用同一运行实例。
 * 所有写操作按 containerKey 串行加锁，防止并发启停互相踩踏。</p>
 */
public interface ContainerLifecycleService {

    /**
     * 启动容器：非可启动态（CREATED/STOPPED/FAILED）则幂等返回当前态；
     * 成功→ phase=RUNNING、回填 containerId；失败→ phase=FAILED、记录 message。
     */
    FunctionContainerInstance start(String containerKey);

    /** 停止并移除容器（docker stop + rm），幂等：未启动或已停止不报错。→ phase=STOPPED */
    FunctionContainerInstance stop(String containerKey);

    /** 重启：先停（best-effort）后启。 */
    FunctionContainerInstance restart(String containerKey);

    /** 回查真实运行状态（docker inspect）并落库，处理被外部停止/崩溃的情况。 */
    FunctionContainerInstance refresh(String containerKey);

    /** 按本体空间列出全部容器实例（启停管理页数据源；spaceId 为空则列全部）。 */
    List<FunctionContainerInstance> listBySpace(Integer spaceId);

    /**
     * 按 containerKey 取运行实例（不存在报错）。
     * <p>供启停入口先读归属节点 {@code node_ip/node_port}，决定本地执行还是转发到归属节点。</p>
     */
    FunctionContainerInstance requireInstance(String containerKey);
}
