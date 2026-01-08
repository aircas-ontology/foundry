package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ApiModel(description = "根据实体id和时间范围查询关系")
public class EntityIdsAndTimeRangeQueryParam extends EntityIdsQueryParam {

    @ApiModelProperty(name = "startTime", value = "开始时间", example = "2025-10-01T12:34:56Z")
    private Date startTime;

    @ApiModelProperty(name = "endTime", value = "结束时间", example = "2026-10-01T12:34:56Z")
    private Date endTime;

}
