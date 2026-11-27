package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "基础函数多对象公共字段查询请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class BasicFunctionCommonPropertyParam {

    @Schema(name = "ontologySpaceId", description = "对象空间id；传了就核对所选对象都属于该空间", example = "156")
    private Integer ontologySpaceId;

    @Schema(name = "ontologyIdentifiers", description = "查询公共字段的对象id列表，至少一个；结果取这些对象共有的字段", required = true,
            example = "[\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\",\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\"]")
    @NotEmpty(message = "ontologyIdentifiers 不能为空")
    private List<String> ontologyIdentifiers;
}