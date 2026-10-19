package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ContainerPhaseEnum;
import com.aircas.ptr.foundry.ontology.model.po.FunctionContainerInstance;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 容器算子运行实例视图：启停管理页列表/操作返回。仅展示容器级与运行态字段（不含镜像/文档大对象）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "容器算子运行实例")
public class ContainerRuntimeVO {

    @Schema(description = "容器实例归组键（启停操作凭此定位）")
    private String containerKey;

    @Schema(description = "所属本体空间 id")
    private Integer ontologySpaceId;

    @Schema(description = "容器算子展示名")
    private String displayName;

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

    @Schema(description = "docker 容器 ID（未启动为空）")
    private String containerId;

    @Schema(description = "最后一次生命周期动作：start/stop/restart/refresh")
    private String lastAction;

    @Schema(description = "最后一次动作时间")
    private Date lastActionAt;

    @Schema(description = "状态描述/失败原因")
    private String message;

    /** PO → VO：容器级/运行态字段直拷；phase 同时给出枚举名与中文标签 */
    public static ContainerRuntimeVO from(FunctionContainerInstance po) {
        if (po == null) {
            return null;
        }
        ContainerPhaseEnum phase = po.getPhase();
        return ContainerRuntimeVO.builder()
                .containerKey(po.getContainerKey())
                .ontologySpaceId(po.getOntologySpaceId())
                .displayName(po.getDisplayName())
                .containerName(po.getContainerName())
                .phase(phase == null ? null : phase.name())
                .phaseLabel(phase == null ? null : phase.getLabel())
                .hostPort(po.getHostPort())
                .containerPort(po.getContainerPort())
                .containerId(po.getContainerId())
                .lastAction(po.getLastAction())
                .lastActionAt(po.getLastActionAt())
                .message(po.getMessage())
                .build();
    }
}
