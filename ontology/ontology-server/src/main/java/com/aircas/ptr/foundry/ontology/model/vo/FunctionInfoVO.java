package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "函数基本信息VO")
public class FunctionInfoVO {

    @Schema(name = "functionApi", description = "函数api", required = true)
    private String functionApi;

    @Schema(name = "displayName", description = "函数名称", required = true)
    private String displayName;

    @Schema(name = "description", description = "描述")
    private String description;

    @Schema(name = "type", description = "函数模型")
    @NotNull(message = "model is null")
    private FunctionModelEnum model;

    @Schema(name = "type", description = "函数类型")
    private FunctionTypeEnum type;

    @Schema(description = "已发布版本 ID 列表")
    private List<Long> publishedFunctionVersionIds;

    @Schema(description = "草稿版本 ID")
    private Long draftFunctionVersionId;

    @Schema(description = "是否存在草稿")
    private Boolean hasDraft;
}
