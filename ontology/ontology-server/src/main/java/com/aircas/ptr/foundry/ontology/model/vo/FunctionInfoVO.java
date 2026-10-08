package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionStatusEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "函数版本基本信息")
public class FunctionInfoVO {
    @Schema(description = "函数版本 ID")
    private Long functionVersionId;
    @Schema(description = "函数 API")
    private String functionApi;
    @Schema(description = "版本号", example = "1.2.31")
    private String version;
    @Schema(description = "版本状态")
    private FunctionStatusEnum versionStatus;
    @Schema(description = "函数名称")
    private String displayName;
    @Schema(description = "描述")
    private String description;
    @Schema(description = "函数模型")
    private FunctionModelEnum model;
    @Schema(description = "函数类型")
    private FunctionTypeEnum type;
}
