package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.aircas.ptr.foundry.ontology.model.enums.ContainerPhaseEnum;
import com.aircas.ptr.foundry.ontology.model.enums.HeartbeatStatusEnum;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 容器算子运行实例视图：启停管理页列表/操作返回。展示容器级与运行态字段（镜像仅取地址，不含完整 JSON）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "容器算子运行实例")
public class ContainerRuntimeVO {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Schema(description = "容器实例归组键（启停操作凭此定位）")
    private String containerKey;

    @Schema(description = "所属本体空间 id")
    private Integer ontologySpaceId;

    @Schema(description = "容器算子展示名")
    private String displayName;

    @Schema(description = "镜像地址（repoTags 首条，如 registry/image:tag）")
    private String imageAddress;

    @Schema(description = "docker 容器名（用户 --name 或平台默认 foundry-ctr-<key>）")
    private String containerName;

    @Schema(description = "运行状态枚举名：CREATED/RUNNING/STOPPING/STOPPED/RESTARTING/FAILED")
    private String phase;

    @Schema(description = "运行状态中文标签")
    private String phaseLabel;

    @Schema(description = "宿主端口（调用入口端口）")
    private Integer hostPort;

    @Schema(description = "容器端口")
    private Integer containerPort;

    @Schema(description = "归属节点 IP（镜像 tar 与容器只在此节点上；为空表示单节点部署或历史数据）")
    private String nodeIp;

    @Schema(description = "归属节点 ontology-server 端口（启停/刷新请求自动转发到此进程）")
    private Integer nodePort;

    @Schema(description = "docker 容器 ID（未启动为空）")
    private String containerId;

    @Schema(description = "最后一次生命周期动作：start/stop/restart/refresh")
    private String lastAction;

    @Schema(description = "最后一次动作时间")
    private Date lastActionAt;

    @Schema(description = "状态描述/失败原因")
    private String message;

    @Schema(description = "心跳状态：HEALTHY/UNHEALTHY（仅 phase=RUNNING 时有意义）")
    private String heartbeatStatus;

    @Schema(description = "心跳状态中文标签")
    private String heartbeatLabel;

    @Schema(description = "最后一次心跳探测时间")
    private Date lastHeartbeatAt;

    /** PO → VO：容器级/运行态字段直拷；phase 同时给出枚举名与中文标签；镜像地址从 image JSON 提取 repoTags 首条；心跳状态同理 */
    public static ContainerRuntimeVO from(FunctionContainerInstance po) {
        if (po == null) {
            return null;
        }
        ContainerPhaseEnum phase = po.getPhase();
        HeartbeatStatusEnum heartbeat = po.getHeartbeatStatus();
        return ContainerRuntimeVO.builder()
                .containerKey(po.getContainerKey())
                .ontologySpaceId(po.getOntologySpaceId())
                .displayName(po.getDisplayName())
                .imageAddress(extractImageAddress(po.getImage()))
                .containerName(po.getContainerName())
                .phase(phase == null ? null : phase.name())
                .phaseLabel(phase == null ? null : phase.getLabel())
                .hostPort(po.getHostPort())
                .containerPort(po.getContainerPort())
                .nodeIp(po.getNodeIp())
                .nodePort(po.getNodePort())
                .containerId(po.getContainerId())
                .lastAction(po.getLastAction())
                .lastActionAt(po.getLastActionAt())
                .message(po.getMessage())
                .heartbeatStatus(heartbeat == null ? null : heartbeat.name())
                .heartbeatLabel(heartbeat == null ? null : heartbeat.getLabel())
                .lastHeartbeatAt(po.getLastHeartbeatAt())
                .build();
    }

    /** 从镜像 JSON 解析 repoTags 首条作为展示地址；解析失败或为空返回 null */
    private static String extractImageAddress(String imageJson) {
        if (imageJson == null || imageJson.isBlank()) {
            return null;
        }
        try {
            ContainerImageInfo info = OBJECT_MAPPER.readValue(imageJson, ContainerImageInfo.class);
            List<String> tags = info.getRepoTags();
            return (tags == null || tags.isEmpty()) ? null : tags.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
