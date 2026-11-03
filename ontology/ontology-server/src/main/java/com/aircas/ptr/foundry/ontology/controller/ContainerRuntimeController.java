package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.aircas.ptr.foundry.ontology.model.vo.ContainerRuntimeVO;
import com.aircas.ptr.foundry.ontology.service.ContainerLifecycleService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.utils.ContainerNodeForwarder;
import com.aircas.ptr.foundry.ontology.utils.ContainerNodeIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 容器算子运行管理：启停管理页的后端接口（列表 / 启动 / 停止 / 重启 / 状态刷新）。
 * <p>以「容器」为维度（containerKey 归组，一容器一实例），所有操作按 containerKey 定位。</p>
 * <p>多实例部署下，容器只能在其归属节点（镜像 tar 与该节点 docker）上起停：列表是纯 DB 读，任意节点可答；
 * 启停/刷新则先读实例的归属 {@code node_ip/node_port}，非本节点时把同一个请求转发过去，前端无需感知。</p>
 */
@Tag(name = "容器算子运行管理")
@RestController
@RequestMapping("/function/container/runtime")
public class ContainerRuntimeController {

    @Resource
    private ContainerLifecycleService containerLifecycleService;

    @Resource
    private FunctionService functionService;

    @Resource
    private ContainerNodeIdentity containerNodeIdentity;

    @Resource
    private ContainerNodeForwarder containerNodeForwarder;

    @Operation(summary = "列出容器运行实例（启停管理页数据源；spaceId 为空则列全部）")
    @GetMapping("/list")
    public RestResult<List<ContainerRuntimeVO>> list(@RequestParam(required = false) Integer spaceId) {
        List<FunctionContainerInstance> instances = containerLifecycleService.listBySpace(spaceId);
        return RestResult.ofData(instances.stream().map(ContainerRuntimeVO::from).collect(Collectors.toList()));
    }

    @Operation(summary = "启动容器（幂等：非可启动态直接返回当前态）")
    @PostMapping("/start")
    public RestResult<ContainerRuntimeVO> start(@RequestParam String containerKey) {
        return RestResult.ofData(control(containerKey, "启动",
                key -> ContainerRuntimeVO.from(containerLifecycleService.start(key))));
    }

    @Operation(summary = "停止并移除容器（幂等：未启动或已停止不报错）")
    @PostMapping("/stop")
    public RestResult<ContainerRuntimeVO> stop(@RequestParam String containerKey) {
        return RestResult.ofData(control(containerKey, "停止",
                key -> ContainerRuntimeVO.from(containerLifecycleService.stop(key))));
    }

    @Operation(summary = "重启容器（先停后启）")
    @PostMapping("/restart")
    public RestResult<ContainerRuntimeVO> restart(@RequestParam String containerKey) {
        return RestResult.ofData(control(containerKey, "重启",
                key -> ContainerRuntimeVO.from(containerLifecycleService.restart(key))));
    }

    @Operation(summary = "刷新容器状态（docker inspect 回查真实运行态并落库）")
    @PostMapping("/refresh")
    public RestResult<ContainerRuntimeVO> refresh(@RequestParam String containerKey) {
        return RestResult.ofData(control(containerKey, "状态刷新",
                key -> ContainerRuntimeVO.from(containerLifecycleService.refresh(key))));
    }

    @Operation(summary = "清理容器算子磁盘文件与实例记录（多节点删除转发入口，归属节点本地执行）")
    @PostMapping("/cleanup_files")
    public RestResult<Void> cleanupFiles(@RequestParam String containerKey) {
        functionService.cleanupContainerLocal(containerKey);
        return RestResult.success();
    }

    /**
     * 生命周期动作统一入口：归属节点非本节点时原样转发（对端返回的 VO 即本端返回类型，无需 PO↔VO 互转），
     * 否则执行本地逻辑。转发的 query 串（containerKey）与 access-token 由转发器按原请求带过去。
     */
    private ContainerRuntimeVO control(String containerKey, String action, Function<String, ContainerRuntimeVO> local) {
        FunctionContainerInstance instance = containerLifecycleService.requireInstance(containerKey);
        if (!containerNodeIdentity.isSelf(instance.getNodeIp()) && !containerNodeForwarder.isForwardedRequest()) {
            return containerNodeForwarder.forwardFor(instance.getNodeIp(), instance.getNodePort(), null,
                    ContainerRuntimeVO.class, action);
        }
        return local.apply(containerKey);
    }
}
