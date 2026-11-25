package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "字典项")
public class DictItemVO {

    @Schema(description = "字典编号")
    private Long id;

    @Schema(description = "字典名称")
    private String dictName;
}
