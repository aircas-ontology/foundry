package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "上传文件列表")
public class UploadFileVO {

    @Schema(description = "文件编号")
    private Long id;

    @Schema(description = "文件名称")
    private String fileName;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "关联本体空间编号，多个用逗号分割")
    private String ontologySpace;

    @Schema(description = "关联本体空间名称")
    private String ontologySpaceName;

    @Schema(description = "文件上传路径")
    private String uploadPath;

    @Schema(description = "抽取进度")
    private Integer extractProgress;

    @Schema(description = "上传人员")
    private String uploader;

    @Schema(description = "抽取时间")
    private Date extractTime;

    @Schema(description = "是否删除：0未删除，1已删除")
    private Integer deleted;
}
