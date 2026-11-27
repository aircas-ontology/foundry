package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建隐含关系总览统计")
public class ImplicitRelationOverviewCreateParam {

    @Schema(description = "隐含关系编号", example = "1")
    @NotNull(message = "隐含关系编号不能为空")
    private Long implicitRelationId;

    @Schema(description = "关联类型", example = "隶属")
    @NotBlank(message = "关联类型不能为空")
    @Size(max = 64, message = "关联类型最长64个字符")
    private String relationType;

    @Schema(description = "名称", example = "舰船")
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称最长128个字符")
    private String name;

    @Schema(description = "数量", example = "1")
    @NotNull(message = "数量不能为空")
    @Min(value = 0, message = "数量不能小于0")
    private Integer quantity;

    @Schema(description = "创建人，不传时默认为admin", example = "admin")
    @Size(max = 64, message = "创建人最长64个字符")
    private String creator;
}
