package com.aircas.ptr.foundry.ontology.model.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class VisibilityWindow {

    @Schema(name = "startTime",description = "未来最近可见窗口开始时间",example = "1769585745344")
    private Date startTime;

    @Schema(name = "endTime",description = "未来最近可见窗口结束时间",example = "1769585745344")
    private Date endTime;

}