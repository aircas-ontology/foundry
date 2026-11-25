package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "创建字典结果")
public class DictCreateVO {

    @Schema(description = "字典编号")
    private Long id;

    @Schema(description = "字典组编号")
    private String groupCode;
}
