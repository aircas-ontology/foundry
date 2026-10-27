package com.aircas.ptr.foundry.agent.server.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 会话参考文档上传结果。
 */
@Data
@Builder
@Schema(description = "会话参考文档上传结果")
public class DocumentUploadVO {

    @Schema(description = "文档文件名", example = "对象说明.md")
    private String fileName;

    @Schema(description = "解析后保留的字符数")
    private Integer charCount;

    @Schema(description = "正文超长是否被截断（上限 8000 字符）")
    private Boolean truncated;
}
