package com.aircas.ptr.foundry.ontology.model.dto.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 容器算子配置，序列化后存入 {@code function.docker_config} 列（仅 type=CONTAINER 有值）。
 * <p>记录上传的镜像信息、接口文档信息，以及从文档中勾选要生成的接口列表。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContainerOperatorConfig {

    /** 镜像信息 */
    private ContainerImageInfo image;

    /** 接口文档信息 */
    private ContainerInterfaceDoc interfaceDoc;

    /** 用户勾选并保存的接口（未勾选的接口不生成算子，不落库） */
    private List<ContainerInterface> interfaces;
}
