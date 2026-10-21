package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 容器算子专属配置（统一创建入口 {@code POST /function} 在 {@code type=CONTAINER} 时携带的嵌套体）。
 * <p>仅承载容器专有字段：镜像、接口文档、启动命令、勾选接口。公共字段
 * （functionApi / displayName / description / ontologySpaceId）沿用 {@link FunctionCreateParam} 顶层字段，
 * 容器专属校验（镜像存在、勾选接口非空、端口 / 容器名唯一等）在容器注册服务内完成。</p>
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "容器算子专属配置（type=CONTAINER 时必填）")
public class ContainerConfig {

    @Schema(name = "image", description = "上传镜像接口返回的镜像信息")
    private ContainerImageInfo image;

    @Schema(name = "interfaceDoc", description = "上传接口文档接口返回的文档信息")
    private ContainerInterfaceDoc interfaceDoc;

    @Schema(name = "runCommand", description = "容器启动命令：完整的 docker run 命令字符串（端口映射、环境变量等均写在其中）。一个容器一条启动命令，多个接口算子共用")
    private String runCommand;

    @Schema(name = "selectedInterfaces", description = "从探查结果中勾选的接口列表（非空，未勾选不生成算子）")
    private List<ContainerInterface> selectedInterfaces;
}
