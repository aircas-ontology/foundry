package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.aircas.ptr.foundry.ontology.model.vo.ContainerRuntimeVO;
import com.aircas.ptr.foundry.ontology.service.ContainerLifecycleService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.utils.ContainerNodeForwarder;
import com.aircas.ptr.foundry.ontology.utils.ContainerNodeIdentity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 容器算子运行管理 MCP 工具（对应 {@code ContainerRuntimeController}，REST 前缀 /function/container/runtime）。
 *
 * <p>以「容器」为维度（containerKey 归组，一容器一实例），覆盖列表 / 启动 / 停止 / 重启 / 状态刷新 / 文件清理。
 * 生命周期动作复刻 controller 的 control() 统一入口：归属节点非本节点时原样转发，否则本地执行。
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class ContainerRuntimeMcpTools implements McpTools {

    private final ContainerLifecycleService containerLifecycleService;
    private final FunctionService functionService;
    private final ContainerNodeIdentity containerNodeIdentity;
    private final ContainerNodeForwarder containerNodeForwarder;

    @Tool(description = """
            列出容器运行实例（对应 REST 端点 GET /function/container/runtime/list）。只读操作，纯 DB 读，任意节点可答。
            当用户要查看容器算子的运行状态（启停管理页数据源）时使用，也是取 containerKey 供启动/停止/重启/刷新用的入口。
            参数：spaceId 可选，对象空间 id，不传列全部空间的容器实例。
            返回 ContainerRuntimeVO 列表：containerKey 容器归组键（启停操作凭此定位）、ontologySpaceId 所属空间、
            displayName 展示名、imageAddress 镜像地址、containerName docker 容器名、
            phase 运行状态（CREATED/RUNNING/STOPPING/STOPPED/RESTARTING/FAILED）及 phaseLabel 中文标签、
            hostPort 宿主端口、containerPort 容器端口、nodeIp/nodePort 归属节点地址、containerId docker 容器 ID（未启动为空）、
            lastAction 最后一次生命周期动作（start/stop/restart/refresh）、lastActionAt 动作时间、message 状态描述/失败原因、
            heartbeatStatus 心跳状态（HEALTHY/UNHEALTHY，仅 RUNNING 时有意义）及 heartbeatLabel、lastHeartbeatAt 心跳探测时间、
            containerSource 容器来源（USER 用户创建可操作 / SYSTEM 系统预置只读）及中文标签。
            无实例时返回空列表。
            """)
    public List<ContainerRuntimeVO> listContainerRuntimes(
            @ToolParam(description = "对象空间 id，不传列全部空间", required = false) Integer spaceId) {
        List<FunctionContainerInstance> instances = containerLifecycleService.listBySpace(spaceId);
        return instances.stream().map(ContainerRuntimeVO::from).collect(Collectors.toList());
    }

    @Tool(description = """
            启动容器（对应 REST 端点 POST /function/container/runtime/start）。
            当需要把某个容器算子运行实例拉起时使用。幂等：非可启动态直接返回当前态，不报错。
            参数：containerKey 必填，容器实例归组键（用 listContainerRuntimes 获取）。
            多节点部署下容器只能在其归属节点（镜像 tar 与该节点 docker）上启动：归属非本节点时请求会按当前
            HTTP 请求上下文原样转发到归属节点（依赖请求线程与 access-token 透传，MCP 入口触发跨节点转发可能受限，
            失败时请到归属节点操作）。
            返回操作后的 ContainerRuntimeVO（字段见 listContainerRuntimes），phase 预期为 RUNNING。
            """)
    public ContainerRuntimeVO startContainer(
            @ToolParam(description = "容器实例归组键 containerKey") String containerKey) {
        requireContainerKey(containerKey);
        return control(containerKey, "启动",
                key -> ContainerRuntimeVO.from(containerLifecycleService.start(key)));
    }

    @Tool(description = """
            停止并移除容器（对应 REST 端点 POST /function/container/runtime/stop，docker stop + rm）。
            当需要停掉某个容器算子运行实例时使用。幂等：未启动或已停止不报错。
            参数：containerKey 必填，容器实例归组键（用 listContainerRuntimes 获取）。
            多节点部署下归属非本节点时请求会按当前 HTTP 请求上下文原样转发到归属节点（同 startContainer 的转发限制）。
            返回操作后的 ContainerRuntimeVO（字段见 listContainerRuntimes），phase 预期为 STOPPED。
            """)
    public ContainerRuntimeVO stopContainer(
            @ToolParam(description = "容器实例归组键 containerKey") String containerKey) {
        requireContainerKey(containerKey);
        return control(containerKey, "停止",
                key -> ContainerRuntimeVO.from(containerLifecycleService.stop(key)));
    }

    @Tool(description = """
            重启容器（对应 REST 端点 POST /function/container/runtime/restart，先停后启）。
            当容器运行异常、需要重新拉起时使用。
            参数：containerKey 必填，容器实例归组键（用 listContainerRuntimes 获取）。
            多节点部署下归属非本节点时请求会按当前 HTTP 请求上下文原样转发到归属节点（同 startContainer 的转发限制）。
            返回操作后的 ContainerRuntimeVO（字段见 listContainerRuntimes），phase 预期为 RUNNING。
            """)
    public ContainerRuntimeVO restartContainer(
            @ToolParam(description = "容器实例归组键 containerKey") String containerKey) {
        requireContainerKey(containerKey);
        return control(containerKey, "重启",
                key -> ContainerRuntimeVO.from(containerLifecycleService.restart(key)));
    }

    @Tool(description = """
            刷新容器状态（对应 REST 端点 POST /function/container/runtime/refresh）。
            当怀疑库中运行态与 docker 真实状态不一致（如容器被外部停掉、启动超时后确认结果）时使用：
            docker inspect 回查真实运行态并落库。
            参数：containerKey 必填，容器实例归组键（用 listContainerRuntimes 获取）。
            多节点部署下归属非本节点时请求会按当前 HTTP 请求上下文原样转发到归属节点（同 startContainer 的转发限制）。
            返回刷新后的 ContainerRuntimeVO（字段见 listContainerRuntimes），phase/heartbeatStatus 为回查后的真实状态。
            """)
    public ContainerRuntimeVO refreshContainer(
            @ToolParam(description = "容器实例归组键 containerKey") String containerKey) {
        requireContainerKey(containerKey);
        return control(containerKey, "状态刷新",
                key -> ContainerRuntimeVO.from(containerLifecycleService.refresh(key)));
    }

    @Tool(description = """
            清理容器算子磁盘文件与实例记录（对应 REST 端点 POST /function/container/runtime/cleanup_files）。
            删除容器算子时的本地清理入口：在归属节点上删除镜像 tar 等磁盘文件并移除运行实例记录。
            通常由删除函数流程联动调用，一般不由对话直接调用；误用会清掉容器文件，请先确认容器已停止且算子确需删除。
            参数：containerKey 必填，容器实例归组键。
            副作用：删除归属节点上的容器磁盘文件与实例记录。成功返回中文结果说明。
            """)
    public String cleanupContainerFiles(
            @ToolParam(description = "容器实例归组键 containerKey") String containerKey) {
        requireContainerKey(containerKey);
        functionService.cleanupContainerLocal(containerKey);
        return "容器算子磁盘文件与实例记录清理成功，containerKey=" + containerKey;
    }

    /**
     * 生命周期动作统一入口（原样复刻 ContainerRuntimeController#control）：
     * 归属节点非本节点时原样转发（对端返回的 VO 即本端返回类型，无需 PO↔VO 互转），否则执行本地逻辑。
     * 转发的 query 串（containerKey）与 access-token 由转发器按原请求带过去。
     */
    private ContainerRuntimeVO control(String containerKey, String action, Function<String, ContainerRuntimeVO> local) {
        FunctionContainerInstance instance = containerLifecycleService.requireInstance(containerKey);
        if (!containerNodeIdentity.isSelf(instance.getNodeIp()) && !containerNodeForwarder.isForwardedRequest()) {
            return containerNodeForwarder.forwardFor(instance.getNodeIp(), instance.getNodePort(), null,
                    ContainerRuntimeVO.class, action);
        }
        return local.apply(containerKey);
    }

    private void requireContainerKey(String containerKey) {
        if (StringUtils.isBlank(containerKey)) {
            throw new IllegalArgumentException("containerKey（容器实例归组键）不能为空");
        }
    }
}