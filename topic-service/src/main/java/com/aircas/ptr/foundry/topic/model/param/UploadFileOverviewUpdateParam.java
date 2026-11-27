package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "修改文件总览统计")
public class UploadFileOverviewUpdateParam {

    @Schema(description = "文件总览统计编号", example = "1")
    @NotNull(message = "文件总览统计编号不能为空")
    private Long id;

    @Schema(description = "文件编号", example = "1")
    @NotNull(message = "文件编号不能为空")
    private Long fileId;

    @Schema(description = "文件总数", example = "1")
    @NotNull(message = "文件总数不能为空")
    @Min(value = 0, message = "文件总数不能小于0")
    private Integer fileTotal;

    @Schema(description = "抽取列表，如[{\"name\":\"实体\",\"quantity\":1,\"proportion\":0.25}]",
            example = "[{\"name\":\"实体\",\"quantity\":1,\"proportion\":0.25}]")
    @NotBlank(message = "抽取列表不能为空")
    private String extractList;

    @Schema(description = "关联目标数量", example = "1")
    @NotNull(message = "关联目标数量不能为空")
    @Min(value = 0, message = "关联目标数量不能小于0")
    private Integer targetCount;

    @Schema(description = "隐含关系", example = "0")
    @NotNull(message = "隐含关系不能为空")
    @Min(value = 0, message = "隐含关系不能小于0")
    private Integer implicitRelation;
}
