package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionRuntimeStatusEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "函数基本信息VO")
public class FunctionInfoVO {

    @Schema(name = "functionApi", description = "函数api", required = true)
    private String functionApi;

    @Schema(name = "version", description = "版本号，格式 x.y.z")
    private String version;

    @Schema(name = "publishStatus", description = "发布状态：0 未发布，1 已发布")
    private Integer publishStatus;

    @Schema(name = "displayName", description = "函数名称", required = true)
    private String displayName;

    @Schema(name = "description", description = "描述")
    private String description;

    @Schema(name = "type", description = "函数模型")
    @NotNull(message = "model is null")
    private FunctionModelEnum model;

    @Schema(name = "type", description = "函数类型")
    private FunctionTypeEnum type;

    @Schema(name = "language", description = "脚本语言（仅 CUSTOMIZE/EXTERNAL 有意义）")
    private FunctionLanguageEnum language;

    @Schema(name = "ontologySpaceId", description = "所属本体空间 id")
    private Integer ontologySpaceId;

    @Schema(name = "createTime", description = "创建时间")
    private Date createTime;

    @Schema(name = "updateTime", description = "更新时间")
    private Date updateTime;

    @Schema(name = "runningStatus", description = "运行状态：RUNNING 运行中 / NOT_RUNNING 未运行")
    private FunctionRuntimeStatusEnum runningStatus;
}
