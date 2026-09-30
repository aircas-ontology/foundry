package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamRoleEnum;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "函数信息")
public class FunctionParameterVO {

    @Schema(name = "paramId",description = "参数id")
    private Long paramId;

    @Schema(name = "paramName",description = "参数名称")
    private String paramName;

    @Schema(name = "paramType",description = "参数类型")
    private FunctionParamTypeEnum paramType;

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
}
