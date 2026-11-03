package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 函数编排校验请求。
 * <p>画布编辑过程中前端实时提交编排定义做校验；也可以只传 {@code functionApi}，
 * 由后端读取库中已落库的编排定义再校验（用于发布前复核）。</p>
 */
@Data
@Builder

@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排校验请求")
public class CompositeValidateParam {

    @Schema(name = "functionApi", description = "编排函数 api，与 compositeConfig 至少传一个")
    private String functionApi;

    @Schema(name = "compositeConfig", description = "待校验的编排定义，为空时取 functionApi 已落库的定义")
    private CompositeConfig compositeConfig;
}
