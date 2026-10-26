package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.ContainerPhaseEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ContainerSourceEnum;
import com.aircas.ptr.foundry.ontology.model.enums.HeartbeatStatusEnum;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 容器算子运行实例：一个容器算子（一镜像多接口，设计 C）对应一行，记录常驻容器的启停状态。
 * <p>同批次生成的 N 条 function 通过 {@code container_key} 关联到本行；启停管理页列表即来自本表。</p>
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "function_container_instance")
public class FunctionContainerInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 容器实例分组键（UUID），同批次派生的多条 function 算子共用 */
    private String containerKey;

    /** 所属本体空间 id */
    private Integer ontologySpaceId;

    /** 容器算子展示名（公共前缀） */
    private String displayName;

    /** docker 容器名：用户 --name 或平台默认 foundry-ctr-<key>，全局唯一（保存时查重） */
    private String containerName;

    /** 镜像信息 JSON（{@link com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo}：文件名/路径/大小/架构/repoTags） */
    private String image;

    /** 接口文档信息 JSON（{@link com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc}：文件名/路径/格式） */
    private String interfaceDoc;

    /** 完整 docker run 启动命令 */
    private String runCommand;

    /** 宿主端口（从启动命令解析固化） */
    private Integer hostPort;

    /** 容器端口（仅供记录） */
    private Integer containerPort;

    /** 归属节点 IP：镜像 tar 与 docker 容器只在此节点上；执行期访问 hostPort 的地址，也是启停请求的转发目标。为空视为本节点（单节点部署/历史数据） */
    private String nodeIp;

    /** 归属节点 ontology-server 的 HTTP 端口：启停/刷新控制请求转发到此进程 */
    private Integer nodePort;

    /** 运行状态 */
    private ContainerPhaseEnum phase;

    /** docker 容器 ID（启动成功后回填） */
    private String containerId;

    /** 最后一次生命周期动作：start/stop/restart */
    private String lastAction;

    /** 最后一次动作时间 */
    private Date lastActionAt;

    /** 状态描述/失败原因 */
    private String message;

    /** 心跳状态：TCP 探活结果（HEALTHY/UNHEALTHY），仅 phase=RUNNING 时有意义 */
    private HeartbeatStatusEnum heartbeatStatus;

    /** 最后一次心跳探测时间 */
    private Date lastHeartbeatAt;

    /** 容器来源：USER 为用户创建（可编辑/启停/删除），SYSTEM 为系统预置（只读，禁止操作） */
    private ContainerSourceEnum containerSource;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
