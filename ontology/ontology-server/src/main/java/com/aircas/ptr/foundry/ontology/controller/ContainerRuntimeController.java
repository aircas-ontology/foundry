package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.aircas.ptr.foundry.ontology.model.vo.ContainerRuntimeVO;
import com.aircas.ptr.foundry.ontology.service.ContainerLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 容器算子运行管理：启停管理页的后端接口（列表 / 启动 / 停止 / 重启 / 状态刷新）。
 * <p>以「容器」为维度（containerKey 归组，一容器一实例），所有操作按 containerKey 定位。</p>
 */
@Tag(name = "容器算子运行管理")
@RestController
@RequestMapping("/function/container/runtime")
public class ContainerRuntimeController {

    @Resource
    private ContainerLifecycleService containerLifecycleService;

    @Operation(summary = "列出容器运行实例（启停管理页数据源；spaceId 为空则列全部）")
    @GetMapping("/list")
    public RestResult<List<ContainerRuntimeVO>> list(@RequestParam(required = false) Integer spaceId) {
        List<FunctionContainerInstance> instances = containerLifecycleService.listBySpace(spaceId);
        return RestResult.ofData(instances.stream().map(ContainerRuntimeVO::from).collect(Collectors.toList()));
    }

    @Operation(summary = "启动容器（幂等：非可启动态直接返回当前态）")
    @PostMapping("/start")
    public RestResult<ContainerRuntimeVO> start(@RequestParam String containerKey) {
        return RestResult.ofData(ContainerRuntimeVO.from(containerLifecycleService.start(containerKey)));
    }

    @Operation(summary = "停止并移除容器（幂等：未启动或已停止不报错）")
    @PostMapping("/stop")
    public RestResult<ContainerRuntimeVO> stop(@RequestParam String containerKey) {
        return RestResult.ofData(ContainerRuntimeVO.from(containerLifecycleService.stop(containerKey)));
    }

    @Operation(summary = "重启容器（先停后启）")
    @PostMapping("/restart")
    public RestResult<ContainerRuntimeVO> restart(@RequestParam String containerKey) {
        return RestResult.ofData(ContainerRuntimeVO.from(containerLifecycleService.restart(containerKey)));
    }

    @Operation(summary = "刷新容器状态（docker inspect 回查真实运行态并落库）")
    @PostMapping("/refresh")
    public RestResult<ContainerRuntimeVO> refresh(@RequestParam String containerKey) {
        return RestResult.ofData(ContainerRuntimeVO.from(containerLifecycleService.refresh(containerKey)));
    }
}
