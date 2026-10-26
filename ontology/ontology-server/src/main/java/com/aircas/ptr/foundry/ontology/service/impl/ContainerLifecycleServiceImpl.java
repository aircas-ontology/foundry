package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.enums.ContainerPhaseEnum;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionContainerInstanceMapper;
import com.aircas.ptr.foundry.ontology.service.ContainerLifecycleService;
import com.aircas.ptr.foundry.ontology.utils.DockerCommandExecutor;
import com.aircas.ptr.foundry.ontology.utils.DockerRunCommandParser;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 容器算子生命周期服务实现：按 containerKey 驱动 docker CLI 并维护 {@code function_container_instance} 运行态。
 * <p>每个 containerKey 一把监视器锁（{@link ConcurrentHashMap} 存放），start/stop/restart/refresh 全程持有，
 * 串行化同一容器的并发操作；不同容器互不阻塞。docker 操作失败不抛出到调用方以外，统一落 phase=FAILED + message。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerLifecycleServiceImpl implements ContainerLifecycleService {

    private final DockerCommandExecutor dockerCommandExecutor;
    private final FunctionContainerInstanceMapper containerInstanceMapper;

    /** 解析实例 image JSON（镜像 tag/路径），启动前判断是否需 docker load */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** containerKey → 监视器锁，按容器串行化启停 */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Override
    public FunctionContainerInstance start(String containerKey) {
        synchronized (lockFor(containerKey)) {
            FunctionContainerInstance instance = requireInstance(containerKey);
            ContainerPhaseEnum phase = instance.getPhase();
            // 非可启动态（运行中/停止中/重启中等）：幂等返回当前态，不重复起容器
            if (phase != null && !phase.canStart()) {
                return instance;
            }
            try {
                ensureImageLoaded(instance);
                DockerCommandExecutor.StartResult result = dockerCommandExecutor.startContainer(
                        instance.getRunCommand(), effectiveName(instance));
                instance.setContainerId(result.containerId());
                instance.setPhase(ContainerPhaseEnum.RUNNING);
                instance.setMessage("容器已启动");
            } catch (Exception e) {
                instance.setPhase(ContainerPhaseEnum.FAILED);
                instance.setMessage("启动失败：" + e.getMessage());
                log.warn("容器启动失败 containerKey={}：{}", containerKey, e.getMessage());
            }
            touch(instance, "start");
            containerInstanceMapper.updateById(instance);
            return instance;
        }
    }

    @Override
    public FunctionContainerInstance stop(String containerKey) {
        synchronized (lockFor(containerKey)) {
            FunctionContainerInstance instance = requireInstance(containerKey);
            stopAndRemoveLocked(instance, containerKey);
            containerInstanceMapper.updateById(instance);
            return instance;
        }
    }

    @Override
    public FunctionContainerInstance restart(String containerKey) {
        synchronized (lockFor(containerKey)) {
            FunctionContainerInstance instance = requireInstance(containerKey);
            // 先停（best-effort：容器可能本就不在），落 STOPPED/中间态
            stopAndRemoveLocked(instance, containerKey);
            containerInstanceMapper.updateById(instance);
            // 再启（复用同一锁，synchronized 可重入）
            return startLocked(instance);
        }
    }

    @Override
    public FunctionContainerInstance refresh(String containerKey) {
        synchronized (lockFor(containerKey)) {
            FunctionContainerInstance instance = requireInstance(containerKey);
            String target = resolveTarget(instance, containerKey);
            String status = dockerCommandExecutor.inspectStatus(target);
            ContainerPhaseEnum mapped = mapDockerStatus(status, instance.getPhase());
            instance.setPhase(mapped);
            // 运行中但 ID 丢失（如外部重建）：回查补全 containerId
            if (mapped == ContainerPhaseEnum.RUNNING && StringUtils.isBlank(instance.getContainerId())) {
                instance.setContainerId(target);
            }
            // 容器已不存在（外部 rm）：清空 containerId
            if (status == null) {
                instance.setContainerId(null);
            }
            instance.setMessage("状态已刷新（docker: " + (status == null ? "not found" : status) + "）");
            touch(instance, "refresh");
            containerInstanceMapper.updateById(instance);
            return instance;
        }
    }

    @Override
    public List<FunctionContainerInstance> listBySpace(Integer spaceId) {
        LambdaQueryWrapper<FunctionContainerInstance> wrapper = new LambdaQueryWrapper<>();
        if (spaceId != null) {
            wrapper.eq(FunctionContainerInstance::getOntologySpaceId, spaceId);
        }
        wrapper.orderByDesc(FunctionContainerInstance::getUpdateTime);
        List<FunctionContainerInstance> list = containerInstanceMapper.selectList(wrapper);
        return list == null ? new ArrayList<>() : list;
    }

    // ---------------------------------------------------------------- 内部辅助

    /** 停止逻辑（须在持有该 key 锁时调用）：docker stop + rm，置 STOPPED；失败置 FAILED。未启动则幂等置 STOPPED。 */
    private void stopAndRemoveLocked(FunctionContainerInstance instance, String containerKey) {
        if (instance.getPhase() == ContainerPhaseEnum.CREATED || StringUtils.isBlank(instance.getContainerId())) {
            // 从未启动或无容器 ID：按名称兜底清理后直接落 STOPPED（幂等，no such container 不报错）
            dockerRemoveQuietly(effectiveName(instance), instance);
            touch(instance, "stop");
            return;
        }
        try {
            dockerCommandExecutor.stop(instance.getContainerId());
            dockerCommandExecutor.remove(instance.getContainerId());
            instance.setContainerId(null);
            instance.setPhase(ContainerPhaseEnum.STOPPED);
            instance.setMessage("容器已停止");
        } catch (Exception e) {
            instance.setPhase(ContainerPhaseEnum.FAILED);
            instance.setMessage("停止失败：" + e.getMessage());
            log.warn("容器停止失败 containerKey={}：{}", containerKey, e.getMessage());
        }
        touch(instance, "stop");
    }

    /** 重启时的启动逻辑（已持有 key 锁，直接操作传入实例并落库由调用方负责） */
    private FunctionContainerInstance startLocked(FunctionContainerInstance instance) {
        try {
            ensureImageLoaded(instance);
            DockerCommandExecutor.StartResult result = dockerCommandExecutor.startContainer(
                    instance.getRunCommand(), effectiveName(instance));
            instance.setContainerId(result.containerId());
            instance.setPhase(ContainerPhaseEnum.RUNNING);
            instance.setMessage("容器已重启");
        } catch (Exception e) {
            instance.setPhase(ContainerPhaseEnum.FAILED);
            instance.setMessage("重启失败：" + e.getMessage());
            log.warn("容器重启失败 containerKey={}：{}", instance.getContainerKey(), e.getMessage());
        }
        touch(instance, "restart");
        containerInstanceMapper.updateById(instance);
        return instance;
    }

    /** 按名称兜底移除残留容器，best-effort（不存在或失败均忽略，不影响状态落 STOPPED） */
    private void dockerRemoveQuietly(String name, FunctionContainerInstance instance) {
        try {
            dockerCommandExecutor.stop(name);
            dockerCommandExecutor.remove(name);
        } catch (Exception ignore) {
            // 容器本就不存在：幂等，忽略
        }
        instance.setContainerId(null);
        instance.setPhase(ContainerPhaseEnum.STOPPED);
        instance.setMessage("容器已停止");
    }

    /** 定位容器的操作目标：优先 containerId，回退容器名 */
    private String resolveTarget(FunctionContainerInstance instance, String containerKey) {
        return StringUtils.isNotBlank(instance.getContainerId())
                ? instance.getContainerId()
                : effectiveName(instance);
    }

    /** 容器实际名：实例已存 containerName（用户 --name 或平台默认），无则按 containerKey 生成 */
    private String effectiveName(FunctionContainerInstance instance) {
        return StringUtils.isNotBlank(instance.getContainerName())
                ? instance.getContainerName()
                : DockerCommandExecutor.defaultContainerName(instance.getContainerKey());
    }

    /**
     * 启动前确保镜像已在本地：优先取 {@code image.repoTags[0]}，缺失时从 {@code runCommand} 解析镜像引用。
     * <p>任一拿到后：{@code docker image inspect} 不存在则 {@code docker load -i <tarPath>}；
     * 两者都拿不到时（镜像信息丢失且命令不可解析）交给 docker run 自行处理。</p>
     * <p>回退到 runCommand 解析旨在为 {@code repoTags} 因历史镜像元数据解析失败而缺失的实例提供兼容，
     * 无需修改启动行为即可自动 load。</p>
     */
    private void ensureImageLoaded(FunctionContainerInstance instance) {
        ContainerImageInfo info = readImage(instance.getImage());
        String imageRef = (info != null && info.getRepoTags() != null && !info.getRepoTags().isEmpty())
                ? info.getRepoTags().get(0)
                : DockerRunCommandParser.parseImageRef(instance.getRunCommand());
        if (StringUtils.isBlank(imageRef)) {
            log.warn("容器启动前无法确定镜像引用（repoTags 与 runCommand 均无），交给 docker run 自行处理：{}",
                    instance.getContainerKey());
            return;
        }
        if (dockerCommandExecutor.isImagePresent(imageRef)) {
            return;
        }
        if (info == null || StringUtils.isBlank(info.getPath())) {
            log.warn("本地无镜像 {}，且无 tar 路径可 load（镜像信息缺失）：{}", imageRef, instance.getContainerKey());
            return;
        }
        log.info("容器启动前本地无镜像 {}，执行 docker load：{}", imageRef, info.getPath());
        dockerCommandExecutor.loadImage(info.getPath());
    }

    /** 解析镜像 JSON；失败返回 null */
    private ContainerImageInfo readImage(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ContainerImageInfo.class);
        } catch (Exception e) {
            log.warn("解析容器镜像 JSON 失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * docker 原始状态映射为 phase：
     * running→RUNNING；created→CREATED；restarting→RESTARTING；removing→STOPPING；exited/dead→STOPPED；
     * null（容器不存在/已被外部删除）：原为 CREATED 则保持 CREATED，否则视为 STOPPED。
     */
    private ContainerPhaseEnum mapDockerStatus(String status, ContainerPhaseEnum current) {
        if (status == null) {
            return current == ContainerPhaseEnum.CREATED ? ContainerPhaseEnum.CREATED : ContainerPhaseEnum.STOPPED;
        }
        switch (status.toLowerCase()) {
            case "running":
                return ContainerPhaseEnum.RUNNING;
            case "created":
                return ContainerPhaseEnum.CREATED;
            case "restarting":
                return ContainerPhaseEnum.RESTARTING;
            case "removing":
                return ContainerPhaseEnum.STOPPING;
            case "exited":
            case "dead":
            default:
                return ContainerPhaseEnum.STOPPED;
        }
    }

    private void touch(FunctionContainerInstance instance, String action) {
        instance.setLastAction(action);
        instance.setLastActionAt(new Date());
    }

    /**
     * 按 containerKey 定位实例（不存在报错）。除内部启停逻辑外，也供控制器先读归属节点决定本地执行还是转发。
     */
    @Override
    public FunctionContainerInstance requireInstance(String containerKey) {
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(containerKey),
                "containerKey 不能为空", HttpStatus.BAD_REQUEST);
        FunctionContainerInstance instance = containerInstanceMapper.selectOne(
                new LambdaQueryWrapper<FunctionContainerInstance>()
                        .eq(FunctionContainerInstance::getContainerKey, containerKey));
        PreconditionUtils.checkArgument(instance != null,
                "容器实例不存在：" + containerKey, HttpStatus.BAD_REQUEST);
        return instance;
    }

    private Object lockFor(String containerKey) {
        return locks.computeIfAbsent(containerKey == null ? "" : containerKey, k -> new Object());
    }
}
