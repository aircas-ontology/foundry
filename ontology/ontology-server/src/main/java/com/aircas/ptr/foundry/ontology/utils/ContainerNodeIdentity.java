package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.config.ContainerOperatorProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本节点身份：容器算子用来判断「这个容器的归属节点是不是我」以及「该把控制请求转发到哪个地址」。
 * <p>多实例部署时每台机器在本地配置里写各自的 {@code container-operator.node.ip}
 * （约定同 {@code xxl.job.executor.ip}：必须是其他节点可达的内网 IP，不能写 127.0.0.1）；
 * {@code node.port} 留空取 {@code server.port}。</p>
 * <p>未配置 {@code node.ip} 即单节点模式：不登记文件归属、不在实例行写归属，
 * 于是新建的容器归属为空、判定为本节点，行为与引入归属概念之前一致。</p>
 */
@Component
@RequiredArgsConstructor
public class ContainerNodeIdentity {

    private final ContainerOperatorProperties properties;

    /** 本进程 HTTP 端口：host 网络部署下即对外可达端口，故 node.port 留空时用它 */
    @Value("${server.port:8080}")
    private int serverPort;

    /** 是否启用节点归属模式（配置了本节点可达 IP） */
    public boolean isMultiNode() {
        return properties.isMultiNode();
    }

    /** 本节点 IP；单节点模式下可能为空 */
    public String nodeIp() {
        return properties.getNode() == null ? null : properties.getNode().getIp();
    }

    /** 本节点 java 进程对外端口：{@code node.port} 覆盖，否则 {@code server.port} */
    public int nodePort() {
        Integer configured = properties.getNode() == null ? null : properties.getNode().getPort();
        return configured != null && configured > 0 ? configured : serverPort;
    }

    /**
     * 归属是否为本节点。
     * <p>只比 IP：同一台机器上的多个进程共用同一块磁盘与同一个 docker 守护进程，落在哪都等价，
     * 无需按端口转发。</p>
     * <p>归属为空（单节点部署、或改造前落的历史行）视为本节点，行为与引入归属概念之前一致；
     * 反之行里点名了某节点而我没配 {@code node.ip}（无从判断是不是我）时返回 false——
     * 宁可把请求转发给被点名的节点，也不要在错误的机器上空跑 docker 起一个没人认领的容器。</p>
     */
    public boolean isSelf(String nodeIp) {
        if (StringUtils.isBlank(nodeIp)) {
            return true;
        }
        String self = nodeIp();
        if (StringUtils.isBlank(self)) {
            return false;
        }
        return nodeIp.trim().equalsIgnoreCase(self.trim());
    }

    /** 本节点描述，供日志与报错文案定位「该去哪个进程操作」 */
    public String describeSelf() {
        return StringUtils.isBlank(nodeIp()) ? "未配置 container-operator.node.ip（单节点模式）"
                : nodeIp() + ":" + nodePort();
    }
}
