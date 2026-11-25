package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "编辑事件主题")
public class EventTopicUpdateParam {

    @Schema(description = "事件主题编号", example = "1")
    @NotNull(message = "事件主题编号不能为空")
    private Long id;

    @Schema(description = "事件名称", example = "区域异常")
    @NotBlank(message = "事件名称不能为空")
    @Size(max = 128, message = "事件名称最长128个字符")
    private String eventName;

    @Schema(description = "事件主题类别", example = "监测")
    @NotBlank(message = "事件主题类别不能为空")
    @Size(max = 64, message = "事件主题类别最长64个字符")
    private String category;

    @Schema(description = "开始时间，由页面传入")
    @NotNull(message = "开始时间不能为空")
    private Date startTime;

    @Schema(description = "结束时间，由页面传入")
    @NotNull(message = "结束时间不能为空")
    private Date endTime;

    @Schema(description = "地理区域，由页面传入", example = "华东")
    @NotBlank(message = "地理区域不能为空")
    @Size(max = 128, message = "地理区域最长128个字符")
    private String geoRegion;

    @Schema(description = "区域点，由页面传入", example = "120.1,30.2;120.3,30.4")
    @NotBlank(message = "区域点不能为空")
    private String regionPoint;

    @Schema(description = "事件主题描述")
    @Size(max = 2000, message = "事件主题描述最长2000个字符")
    private String description;

    @Schema(description = "关联本体空间，多个用逗号分割", example = "space-a,space-b")
    @Size(max = 1024, message = "关联本体空间最长1024个字符")
    private String ontologySpace;

    @Schema(description = "状态：0执行中，1执行成功，2执行失败", example = "0")
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为0、1或2")
    @Max(value = 2, message = "状态只能为0、1或2")
    private Integer status;
}
