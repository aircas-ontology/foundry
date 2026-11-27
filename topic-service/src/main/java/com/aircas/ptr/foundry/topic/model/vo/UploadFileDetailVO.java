package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "上传文件详情")
public class UploadFileDetailVO {

    @Schema(description = "文件编号")
    private Long id;

    @Schema(description = "文件名称")
    private String fileName;

    @Schema(description = "是否删除：0未删除，1已删除")
    private Integer deleted;
}
