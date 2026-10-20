package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数列表查询参数")
public class FunctionListQueryParam {

    @Schema(name = "ontologySpaceId", description = "空间 id")
    private Integer ontologySpaceId;

    @Schema(name = "displayName", description = "函数名称（模糊搜索）")
    private String displayName;

    @Schema(name = "type", description = "函数类型：CUSTOMIZE / EXTERNAL / BASIC_QUERY / COMPOSITE / CONTAINER")
    private FunctionTypeEnum type;

    @Schema(name = "publishStatus", description = "发布状态：0 未发布，1 已发布")
    private Integer publishStatus;

    @Schema(name = "startDate", description = "创建开始日期，格式 yyyy-MM-dd")
    private String startDate;

    @Schema(name = "endDate", description = "创建结束日期，格式 yyyy-MM-dd")
    private String endDate;

    @Schema(name = "pageNum", description = "页号，默认 1")
    private Integer pageNum = 1;

    @Schema(name = "pageSize", description = "每页条数，默认 10")
    private Integer pageSize = 10;
}
