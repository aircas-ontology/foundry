package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerImageInfo;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterfaceDoc;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 容器算子保存入参（创建向导最终提交：通用信息 + 镜像 + 接口文档 + 勾选的接口列表）。
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "容器算子保存请求")
public class ContainerFunctionCreateParam {

    @Schema(name = "functionApi", description = "容器算子基础 api（多个接口各自生成算子时，真实 api = 此值 + \"_\" + 接口 api）")
    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    @Schema(name = "displayName", description = "算子名称公共前缀（每条生成算子的名称 = 此值 + \"-\" + 接口名称）")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "description", description = "描述公共前缀（与接口描述拼接）")
    private String description;

    @Schema(name = "ontologySpaceId", description = "所属本体空间 id")
    private Integer ontologySpaceId;

    @Schema(name = "image", description = "上传镜像接口返回的镜像信息")
    private ContainerImageInfo image;

    @Schema(name = "runCommand", description = "容器启动命令：完整的 docker run 命令字符串（端口映射、环境变量等均写在其中）。一个容器一条启动命令，多个接口算子共用")
    @NotBlank(message = "启动命令不能为空")
    private String runCommand;

    @Schema(name = "interfaceDoc", description = "上传接口文档接口返回的文档信息")
    private ContainerInterfaceDoc interfaceDoc;

    @Schema(name = "selectedInterfaces", description = "从探查结果中勾选的接口列表（非空，未勾选不生成算子）")
    @NotEmpty(message = "至少勾选一个接口")
    private List<ContainerInterface> selectedInterfaces;
}
