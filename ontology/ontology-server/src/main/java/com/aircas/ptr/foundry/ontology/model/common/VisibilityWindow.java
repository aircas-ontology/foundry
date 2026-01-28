package com.aircas.ptr.foundry.ontology.model.common;

import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class VisibilityWindow {

    @ApiModelProperty(name = "startTime",value = "未来最近可见窗口开始时间",example = "1769585745344")
    private Date startTime;

    @ApiModelProperty(name = "endTime",value = "未来最近可见窗口结束时间",example = "1769585745344")
    private Date endTime;

}