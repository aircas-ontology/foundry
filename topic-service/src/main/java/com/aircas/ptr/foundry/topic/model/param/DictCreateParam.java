package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建字典")
public class DictCreateParam {

    @Schema(description = "字典名称", example = "男")
    @NotBlank(message = "字典名称不能为空")
    @Size(max = 128, message = "字典名称最长128个字符")
    private String dictName;

    @Schema(description = "字典组名称", example = "性别")
    @NotBlank(message = "字典组名称不能为空")
    @Size(max = 128, message = "字典组名称最长128个字符")
    private String groupName;

    @Schema(description = "创建人", example = "admin")
    @NotBlank(message = "创建人不能为空")
    @Size(max = 64, message = "创建人最长64个字符")
    private String creator;
}
