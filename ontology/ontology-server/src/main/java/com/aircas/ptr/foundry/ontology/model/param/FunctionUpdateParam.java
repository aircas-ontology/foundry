package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.FunctionApiVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.aircas.ptr.foundry.ontology.constant.FunctionConstant.VERSION_PATTERN;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数更新请求")
public class FunctionUpdateParam {


    @Schema(name = "functionApi", description = "函数api", required = true)
    @NotBlank(message = "functionApi is empty")
    @FunctionApiVerify
    private String functionApi;

    @Schema(name = "version", description = "版本号（必填，格式 x.y.z），仅用于定位要修改的未发布版本；修改不会变更版本号（需要新版本请走创建接口）", required = true)
    @NotBlank(message = "version is empty")
    @Pattern(regexp = VERSION_PATTERN, message = "版本号格式必须为 x.y.z（如 1.0.0）")
    private String version;

    @Schema(name = "description", description = "描述")
    private String description;

    @Schema(name = "displayName", description = "函数名称（修改时不可变更，此字段忽略）")
    private String displayName;

    @Schema(name = "code", description = "自定义函数：函数代码")
    private String code;

    @Schema(name = "referenceName", description = "外部函数：函数全限定名")
    private String referenceName;

    @Schema(name = "model", description = "函数模型（可选，不传时保留原值）")
    private FunctionModelEnum model;

    /**
     * 基础查询算子的查询模板配置，仅 type=BASIC_QUERY 时传递。
     * 后端将其序列化为 JSON 存入 code 字段，并自动提取变量存入 function_param。
     */
    @Schema(name = "queryConfig", description = "基础查询算子配置（仅 type=BASIC_QUERY 时必填）")
    private BasicQueryConfig queryConfig;
}
