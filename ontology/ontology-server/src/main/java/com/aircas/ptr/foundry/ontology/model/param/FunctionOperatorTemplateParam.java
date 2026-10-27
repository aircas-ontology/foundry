package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 函数算子代码模板入参：只声明用户可写的字段，ID 由库自增、创建/更新时间由后台填充，不接受请求传入。
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "函数算子代码模板入参")
public class FunctionOperatorTemplateParam {

    @Schema(name = "name", description = "模板名称（全局唯一）", example = "Groovy 文本转整数模板")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 50, message = "模板名称最长 50 字")
    private String name;

    @Schema(name = "language", description = "脚本语言：GROOVY/JAVA/PYTHON/TYPESCRIPT", example = "GROOVY")
    @NotNull(message = "脚本语言不能为空")
    private FunctionLanguageEnum language;

    @Schema(name = "description", description = "模板说明")
    @Size(max = 500, message = "模板说明最长 500 字")
    private String description;

    @Schema(name = "code", description = "模板代码内容")
    @NotBlank(message = "模板代码不能为空")
    private String code;
}
