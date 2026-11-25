package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamRoleEnum;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "函数信息")
public class FunctionParameterVO {

    @Schema(name = "paramId",description = "参数id")
    private Long paramId;

    @Schema(name = "paramName",description = "参数名称")
    private String paramName;

    @Schema(name = "paramType",description = "参数类型文本：集合参数保留完整泛型（如 List<Double>、List<List<Point>>），其余为枚举名（DOUBLE/OBJECT）")
    private String paramType;

    @Schema(name = "category",description = "参数输入输出类别")
    private FunctionParamCategoryEnum category;

    @Schema(name = "paramOrder",description = "参数顺序")
    private Integer paramOrder;

    @Schema(name = "paramOrder",description = "参数schema")
    private String paramSchema;

    @Schema(name = "description",description = "参数描述")
    private String description;

    @Schema(name = "paramRole", description = "参数角色：AGGREGATION-聚合目标 / FILTER-过滤条件（仅 BASIC_QUERY 类型返回）")
    private FunctionParamRoleEnum paramRole;

    @Schema(name = "fields", description = "子字段列表（参数类型为自定义类时展开，标量参数为 null）")
    private List<FunctionParameterVO> fields;

    /** 执行链路用的类型枚举，按 paramType 文本还原。 */
    public FunctionParamTypeEnum paramTypeEnum() {
        return FunctionParamTypeEnum.fromTypeText(paramType);
    }
}
