package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新隐含关系")
public class ImplicitRelationUpdateParam {

    @Schema(description = "编号", example = "1")
    @NotNull(message = "编号不能为空")
    private Long id;

    @Schema(description = "起始节点编号", example = "N1001")
    @NotBlank(message = "起始节点编号不能为空")
    @Size(max = 64, message = "起始节点编号最长64个字符")
    private String startNodeCode;

    @Schema(description = "起始节点名称", example = "舰船A")
    @NotBlank(message = "起始节点名称不能为空")
    @Size(max = 128, message = "起始节点名称最长128个字符")
    private String startNodeName;

    @Schema(description = "所属空间", example = "space-a")
    @NotBlank(message = "所属空间不能为空")
    @Size(max = 128, message = "所属空间最长128个字符")
    private String belongSpace;

    @Schema(description = "关联类型，由页面传入", example = "隶属")
    @NotBlank(message = "关联类型不能为空")
    @Size(max = 64, message = "关联类型最长64个字符")
    private String relationType;

    @Schema(description = "关系跳数，由页面传入", example = "2")
    @NotNull(message = "关系跳数不能为空")
    @Min(value = 0, message = "关系跳数不能小于0")
    private Integer hopCount;

    @Schema(description = "隐含关系数", example = "0")
    @NotNull(message = "隐含关系数不能为空")
    @Min(value = 0, message = "隐含关系数不能小于0")
    private Integer implicitCount;

    @Schema(description = "进度状态：0未发现，1待校验，2已完成", example = "0")
    @NotNull(message = "进度状态不能为空")
    @Min(value = 0, message = "进度状态只能为0、1或2")
    @Max(value = 2, message = "进度状态只能为0、1或2")
    private Integer progressStatus;

    @Schema(description = "更新人，不传时默认为admin", example = "admin")
    @Size(max = 64, message = "更新人最长64个字符")
    private String updater;
}
