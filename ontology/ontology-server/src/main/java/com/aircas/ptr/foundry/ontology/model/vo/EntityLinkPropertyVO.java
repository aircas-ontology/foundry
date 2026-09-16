package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@Schema(description = "实体关系属性VO")
public class EntityLinkPropertyVO {


    /**
     * link的名称
     */
    @Schema(name = "linkName", description = "关系名称")
    private String linkName;

    @Schema(name = "linkType", description = "关系类型")
    private OntologyLinkTypeEnum linkType;

    @Schema(name = "ontologyFrom", description = "本体开始id")
    private String ontologyFrom;

    @Schema(name = "ontologyTo", description = "本体结束id")
    private String ontologyTo;

    @Schema(name = "entityPrimaryKeyFrom", description = "开始实体主键值")
    private Object entityPrimaryKeyFrom;

    @Schema(name = "entityPrimaryKeyTo", description = "结束实体主键值")
    private Object entityPrimaryKeyTo;

    @Schema(name = "entityNodeFrom", description = "开始实体标识")
    private String entityNodeFrom;

    @Schema(name = "displayNameFrom", description = "开始实体展示名称")
    private String displayNameFrom;

    @Schema(name = "entityNodeTo", description = "结束实体标识")
    private String entityNodeTo;

    @Schema(name = "displayNameTo", description = "结束实体展示名称")
    private String displayNameTo;

    @Schema(name = "startTime", description = "最近可见窗口开始时间")
    private Date startTime;

    @Schema(name = "endTime", description = "最近可见窗口结束时间")
    private Date endTime;


    @Schema(name = "visibilityWindows", description = "可见窗口列表")
    private List<VisibilityWindow> visibilityWindows;


    @Schema(name = "status", description = "关系状态")
    private Status status;

}
