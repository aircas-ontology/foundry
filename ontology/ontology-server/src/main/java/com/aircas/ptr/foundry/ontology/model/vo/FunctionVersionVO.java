package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "函数版本信息")
public class FunctionVersionVO {
    private Long functionVersionId;
    private String functionApi;
    private String version;
    private FunctionStatusEnum versionStatus;
    private String changeLog;
    private String createBy;
    private Date publishTime;
    private Date createTime;
}
