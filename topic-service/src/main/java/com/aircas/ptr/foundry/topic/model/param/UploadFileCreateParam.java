package com.aircas.ptr.foundry.topic.model.param;

import com.aircas.ptr.foundry.common.constant.DateFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "创建上传文件")
public class UploadFileCreateParam {

    @Schema(description = "文件名称", example = "区域数据.xlsx")
    @NotBlank(message = "文件名称不能为空")
    @Size(max = 256, message = "文件名称最长256个字符")
    private String fileName;

    @Schema(description = "文件类型", example = "xlsx")
    @NotBlank(message = "文件类型不能为空")
    @Size(max = 64, message = "文件类型最长64个字符")
    private String fileType;

    @Schema(description = "关联本体空间编号，多个用逗号分割", example = "156,157")
    @Size(max = 1024, message = "关联本体空间编号最长1024个字符")
    private String ontologySpace;

    @Schema(description = "关联本体空间名称", example = "海上空间")
    @Size(max = 1024, message = "关联本体空间名称最长1024个字符")
    private String ontologySpaceName;

    @Schema(description = "文件上传路径。网盘上传后续补充")
    @Size(max = 1024, message = "文件上传路径最长1024个字符")
    private String uploadPath;

    @Schema(description = "抽取进度，不传默认为0", example = "0")
    @Min(value = 0, message = "抽取进度不能小于0")
    private Integer extractProgress;

    @Schema(description = "上传人员，由页面传入，不传时默认为admin", example = "admin")
    @Size(max = 64, message = "上传人员最长64个字符")
    private String uploader;

    @Schema(description = "抽取时间", example = "2026-11-09 00:00:00")
    @JsonFormat(pattern = DateFormat.DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date extractTime;
}
