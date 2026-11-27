package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "文件总览统计汇总")
public class UploadFileOverviewStatVO {

    @Schema(description = "文件总数")
    private Integer fileTotal;

    @Schema(description = "关联目标数量")
    private Integer targetCount;

    @Schema(description = "隐含关系")
    private Integer implicitRelation;

    @Schema(description = "抽取列表，相同名称的数量和占比已求和")
    private List<ExtractItemVO> extractList;
}
