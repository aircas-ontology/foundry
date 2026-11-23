package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "按字典组查询")
public class DictQueryParam {

    @Schema(description = "字典组编号", example = "GENDER")
    @NotBlank(message = "字典组编号不能为空")
    @Size(max = 64, message = "字典组编号最长64个字符")
    private String groupCode;
}
