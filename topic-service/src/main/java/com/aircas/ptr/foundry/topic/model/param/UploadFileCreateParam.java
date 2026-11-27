package com.aircas.ptr.foundry.topic.model.param;

import com.aircas.ptr.foundry.common.constant.DateFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@Schema(description = "创建上传文件。文件本身用表单字段 file 上传，只支持单个文件")
public class UploadFileCreateParam {

    @Schema(description = "关联本体空间编号，多个用逗号分割", example = "156,157")
    @Size(max = 1024, message = "关联本体空间编号最长1024个字符")
    private String ontologySpace;

    @Schema(description = "关联本体空间名称", example = "海上空间")
    @Size(max = 1024, message = "关联本体空间名称最长1024个字符")
    private String ontologySpaceName;

    @Schema(description = "抽取进度，不传默认为0", example = "0")
    @Min(value = 0, message = "抽取进度不能小于0")
    private Integer extractProgress;

    @Schema(description = "上传人员，由页面传入，不传时默认为admin", example = "admin")
    @Size(max = 64, message = "上传人员最长64个字符")
    private String uploader;

    @Schema(description = "抽取时间", example = "2026-11-09 00:00:00")
    @DateTimeFormat(pattern = DateFormat.DATE_FORMAT_DEFAULT)
    private Date extractTime;
}
