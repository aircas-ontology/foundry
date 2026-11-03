package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.aircas.ptr.foundry.ontology.model.enums.ContainerPhaseEnum;
import com.aircas.ptr.foundry.ontology.model.enums.HeartbeatStatusEnum;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionContainerInstanceMapper;
import com.aircas.ptr.foundry.ontology.utils.ContainerNodeIdentity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.util.List;

/**
 * 容器心跳监测服务：定时对 RUNNING 状态的容器做 TCP 探活，更新心跳状态。
 * <p>每个节点只探自己归属的容器（node_ip 为本机或为空），不做跨节点转发。</p>
 * <p>探活逻辑：TCP connect 到 hostPort，连通即 HEALTHY，超时或拒绝即 UNHEALTHY。</p>
 * <p>由 XXL-Job 调度（任务名 {@link Constants#CONTAINER_HEARTBEAT_JOB_NAME}），调度周期在 XXL-Job 控制台配置。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerHeartbeatService {

    private static final int TCP_TIMEOUT_MS = 3000;

    private final FunctionContainerInstanceMapper containerInstanceMapper;
    private final ContainerNodeIdentity containerNodeIdentity;

    /**
     * 心跳探测：查本节点所有 RUNNING 容器，逐个 TCP 探活并回填状态。
     * <p>由 XXL-Job 触发，调度周期在控制台配置（建议 30 秒）。</p>
     */
    @XxlJob(Constants.CONTAINER_HEARTBEAT_JOB_NAME)
    public void probeHeartbeat() {
        List<FunctionContainerInstance> runningContainers = listRunningContainersOnThisNode();
        if (runningContainers.isEmpty()) {
            log.debug("心跳探测：本节点无 RUNNING 容器");
            return;
        }
        log.info("心跳探测：本节点 {} 个 RUNNING 容器", runningContainers.size());
        for (FunctionContainerInstance instance : runningContainers) {
            probeOne(instance);
        }
    }

    /** 列出本节点 RUNNING 容器：多节点模式只查 node_ip = 本机的，单节点模式查 node_ip 为空的 */
    private List<FunctionContainerInstance> listRunningContainersOnThisNode() {
        LambdaQueryWrapper<FunctionContainerInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FunctionContainerInstance::getPhase, ContainerPhaseEnum.RUNNING);
        String selfIp = containerNodeIdentity.nodeIp();
        if (selfIp == null) {
            // 单节点模式：只查 node_ip 为空的（历史数据 + 单节点创建的）
            wrapper.isNull(FunctionContainerInstance::getNodeIp);
        } else {
            // 多节点模式：只查明确归属本机的，不探 node_ip 为空的旧数据（避免多节点重复）
            wrapper.eq(FunctionContainerInstance::getNodeIp, selfIp);
        }
        List<FunctionContainerInstance> list = containerInstanceMapper.selectList(wrapper);
        return list == null ? List.of() : list;
    }

    /** 对单个容器做 TCP 探活并更新心跳状态 */
    private void probeOne(FunctionContainerInstance instance) {
        String containerKey = instance.getContainerKey();
        Integer hostPort = instance.getHostPort();
        if (hostPort == null || hostPort <= 0) {
            log.warn("容器 {} 无有效 hostPort，跳过心跳探测", containerKey);
            return;
        }
        String targetIp = instance.getNodeIp() != null ? instance.getNodeIp() : "127.0.0.1";
        boolean alive = tcpProbe(targetIp, hostPort);
        HeartbeatStatusEnum newStatus = alive ? HeartbeatStatusEnum.HEALTHY : HeartbeatStatusEnum.UNHEALTHY;
        Date now = new Date();

        LambdaUpdateWrapper<FunctionContainerInstance> update = new LambdaUpdateWrapper<>();
        update.eq(FunctionContainerInstance::getId, instance.getId())
                .set(FunctionContainerInstance::getHeartbeatStatus, newStatus)
                .set(FunctionContainerInstance::getLastHeartbeatAt, now);
        containerInstanceMapper.update(null, update);

        if (log.isDebugEnabled()) {
            log.debug("容器 {} 心跳探测：{} → {}", containerKey, targetIp + ":" + hostPort, newStatus);
        }
    }

    /** TCP 探活：尝试连接指定 IP:Port，连通返回 true */
    private boolean tcpProbe(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), TCP_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
