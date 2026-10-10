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
import jakarta.validation.constraints.NotNull;
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

    @Schema(name = "version", description = "版本号；缺省时作用于该 api 的最新版本。仅修改未发布版本时允许变更，变更后的值必须大于其余版本的最大版本号")
    @Pattern(regexp = VERSION_PATTERN, message = "版本号格式必须为 x.y.z（如 1.0.0）")
    private String version;

    @Schema(name = "copyToNewVersion", description = "是否复制为新版本：目标版本已发布时为 true，将内容另存为 version 指定的新版本（未发布），原发布版本不动；目标版本未发布时忽略该字段，直接原地修改")
    private Boolean copyToNewVersion;

    @Schema(name = "description", description = "描述")
    private String description;

    @Schema(name = "displayName", description = "函数名称")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "code", description = "自定义函数：函数代码")
    private String code;

    @Schema(name = "referenceName", description = "外部函数：函数全限定名")
    private String referenceName;

    @Schema(name = "type", description = "函数模型")
    @NotNull(message = "model is null")
    private FunctionModelEnum model;

    /**
     * 基础查询算子的查询模板配置，仅 type=BASIC_QUERY 时传递。
     * 后端将其序列化为 JSON 存入 code 字段，并自动提取变量存入 function_param。
     */
    @Schema(name = "queryConfig", description = "基础查询算子配置（仅 type=BASIC_QUERY 时必填）")
    private BasicQueryConfig queryConfig;
}
