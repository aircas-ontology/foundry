package com.aircas.ptr.foundry.ontology.model.dto.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 容器算子配置：详情接口返回的展示/传输模型（{@code FunctionDetailVO.dockerConfig}）。
 * <p>写入 {@code function.docker_config} 时仅包含 {@code interfaces}（接口级）；
 * 镜像/接口文档/启动命令/端口等容器级字段存于 {@code function_container_instance}，
 * 详情回显时由服务层按 container_key 查实例后重新组装本对象，前端契约保持完整。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContainerOperatorConfig {

    /** 镜像信息（容器级，来于 function_container_instance.image） */
    private ContainerImageInfo image;

    /** 容器启动命令：完整的 docker run 命令字符串（端口/环境变量等均含在内），一个容器一条，多接口算子共用 */
    private String runCommand;

    /** 从 runCommand 解析出的宿主端口（执行期拼接调用地址用，保存时固化） */
    private Integer hostPort;

    /** 从 runCommand 解析出的容器端口（仅供记录/诊断） */
    private Integer containerPort;

    /** 接口文档信息 */
    private ContainerInterfaceDoc interfaceDoc;

    /** 用户勾选并保存的接口（未勾选的接口不生成算子，不落库） */
    private List<ContainerInterface> interfaces;
}
