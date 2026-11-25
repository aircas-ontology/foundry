package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建主题")
public class TopicCreateParam {

    @Schema(description = "名称", example = "示例主题")
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称最长128个字符")
    private String name;

    @Schema(description = "说明")
    @Size(max = 512, message = "说明最长512个字符")
    private String description;
}
