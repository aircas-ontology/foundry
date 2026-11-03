package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.dto.container.ContainerInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Swagger 接口导入 → 外部API算子配置的转换请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Swagger 接口转换请求")
public class SwaggerConvertParam {

    @Schema(name = "baseUrl", description = "接口基地址（如 http://host:port），与探查结果中的 path 拼接为完整 endpoint")
    @NotBlank(message = "baseUrl is empty")
    private String baseUrl;

    @Schema(name = "interfaceSpec", description = "探查返回的单个接口定义（含 method/path/出入参）")
    @NotNull(message = "interfaceSpec is null")
    private ContainerInterface interfaceSpec;
}
