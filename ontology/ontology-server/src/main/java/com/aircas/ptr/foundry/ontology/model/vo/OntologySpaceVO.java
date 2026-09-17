package com.aircas.ptr.foundry.ontology.model.vo;


import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体空间VO")
public class OntologySpaceVO {

    @Schema(name = "icon", description = "空间图标url")
    private String iconUrl;

    @Schema(name = "displayName", description = "空间名称", example = "xxx战场")
    private String displayName;

    @Schema(name = "apiName", description = "空间api名称", example = "space_a")
    private String apiName;

    @Schema(name = "description", description = "空间描述", example = "这是空间描述")
    private String description;

    @Schema(name = "spaceId", description = "空间id", example = "1")
    private Integer spaceId;

    /**
     * 本体数量统计
     */
    @Schema(name = "ontologyCount", description = "本体数量", example = "10")
    private Integer ontologyCount;


    @Schema(description = "行为统计")
    private Integer actionCount;

    @Schema(description = "本体属性统计")
    private Integer propertyCount;


    /**
     * 本体关系统计
     */
    @Schema(description = "本体关系统计")
    private Integer linkCount;
    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @Schema(description = "创建时间")
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @Schema(description = "修改时间")
    private Date updateTime;
}
