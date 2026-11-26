package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "隐含关系分页查询。关键词可以不传，不传时查询全部数据")
public class ImplicitRelationQueryParam {

    @Schema(description = "关键词，模糊匹配起始节点名称、编号、关联类型")
    @Size(max = 128, message = "关键词最长128个字符")
    private String keyword;

    @Schema(description = "页码，从1开始", example = "1")
    @Min(value = 1, message = "pageNum最小为1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "pageSize最小为1")
    @Max(value = 200, message = "pageSize最大为200")
    private Integer pageSize = 10;
}
