package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "函数参数定义（前端绑定 UI 用）")
public class FunctionParamVO {

    @Schema(description = "函数参数 id（与 mappingIns[].functionParamId 匹配）", example = "2295")
    private Long paramId;

    @Schema(description = "参数名", example = "max_mach")
    private String paramName;

    @Schema(description = "参数类型", example = "DOUBLE")
    private FunctionParamTypeEnum paramType;

    @Schema(description = "分类：INPUT 入参 / OUTPUT 出参", example = "INPUT")
    private FunctionParamCategoryEnum category;

    @Schema(description = "参数顺序", example = "1")
    private Integer paramOrder;

    @Schema(description = "全限定类名", example = "java.lang.Double")
    private String referenceType;

    @Schema(description = "参数描述", example = "最大速度")
    private String description;

    @Schema(description = "子字段列表（对象/自定义类参数展开，各层含 description；标量参数为 null）")
    private List<FunctionParamVO> fields;
}
