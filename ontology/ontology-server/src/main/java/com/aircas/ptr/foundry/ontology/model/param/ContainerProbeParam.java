package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口文档探查入参：上传接口文档返回的路径。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "容器算子接口探查请求")
public class ContainerProbeParam {

    @Schema(name = "docPath", description = "上传接口文档接口返回的存储路径")
    @NotBlank(message = "docPath is empty")
    private String docPath;
}
