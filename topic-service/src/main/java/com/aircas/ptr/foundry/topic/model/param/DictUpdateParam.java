package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "编辑字典")
public class DictUpdateParam {

    @Schema(description = "字典编号", example = "1")
    @NotNull(message = "字典编号不能为空")
    private Long id;

    @Schema(description = "字典名称", example = "男")
    @NotBlank(message = "字典名称不能为空")
    @Size(max = 128, message = "字典名称最长128个字符")
    private String dictName;

    @Schema(description = "字典组编号", example = "GENDER")
    @NotBlank(message = "字典组编号不能为空")
    @Size(max = 64, message = "字典组编号最长64个字符")
    private String groupCode;

    @Schema(description = "字典组名称", example = "性别")
    @NotBlank(message = "字典组名称不能为空")
    @Size(max = 128, message = "字典组名称最长128个字符")
    private String groupName;

    @Schema(description = "字典状态：0正常，1删除", example = "0")
    @NotNull(message = "字典状态不能为空")
    @Min(value = 0, message = "字典状态只能为0或1")
    @Max(value = 1, message = "字典状态只能为0或1")
    private Integer status;
}
