package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "上传文件列表查询。关键词可以不传，不传时返回全部未删除数据")
public class UploadFileQueryParam {

    @Schema(description = "关键词，模糊匹配文件名称、编号、关联本体空间名称")
    @Size(max = 256, message = "关键词最长256个字符")
    private String keyword;
}
