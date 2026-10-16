package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 脚本参数解析请求：前端把脚本源码与语言传过来，后端 AST 解析出输入/输出参数。
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "脚本参数解析请求")
public class ScriptParseParam {

    @Schema(name = "language", description = "脚本语言：GROOVY/JAVA/PYTHON/TYPESCRIPT")
    @NotNull(message = "language 不能为空")
    private FunctionLanguageEnum language;

    @Schema(name = "code", description = "脚本源码")
    @NotBlank(message = "code 不能为空")
    @Size(max = 200000, message = "code 长度超过上限（200000 字符）")
    private String code;
}
