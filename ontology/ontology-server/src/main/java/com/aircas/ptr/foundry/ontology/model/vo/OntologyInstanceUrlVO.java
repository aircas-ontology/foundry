package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * 领域空间实例 URL
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "实例 URL")
public class OntologyInstanceUrlVO {

    @Schema(description = "主键 id")
    private Long id;

    @Schema(description = "所属对象空间 id")
    private Integer spaceId;

    @Schema(description = "对象 id（ontology_meta.unique_identifier）")
    private String objectId;

    @Schema(description = "实例 id（实例主键值）")
    private String instanceId;

    @Schema(description = "实例对应的 URL")
    private String url;

    @Schema(description = "存入时间；同一实例再次提交后为本次写入时间")
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;
}
