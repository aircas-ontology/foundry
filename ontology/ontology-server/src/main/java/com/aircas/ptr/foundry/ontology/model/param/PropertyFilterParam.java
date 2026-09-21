package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.QueryOpEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "单属性过滤请求")
public class PropertyFilterParam {

    /** 属性apiName名，如 "id" */
    @Schema(name = "propertyApiName", description = "属性apiName",example = "name")
    private String propertyApiName;

    /** 操作符，默认 EQ */
    @Schema(name = "op", description = "操作符 ： EQ, NE,\n" +
            "    LIKE, LIKE_LEFT, LIKE_RIGHT,\n" +
            "    IN, NOT_IN,\n" +
            "    BETWEEN, NOT_BETWEEN,\n" +
            "    GT, GE, LT, LE,\n" +
            "    IS_NULL, IS_NOT_NULL,",example = "EQ")
    private QueryOpEnum op = QueryOpEnum.EQ;

    /** 单值（EQ/GT/LIKE/... 用） */
    @Schema(name = "value", description = "单值（EQ/GT/LIKE/... 用）",example = "1")
    private Object value;

    /** 多值（IN/BETWEEN 用，BETWEEN 约定 [lower, upper]） */
    @Schema(name = "values", description = "多值（IN/BETWEEN 用，BETWEEN 约定 [lower, upper]",example = "[10,20]")
    private List<Object> values;
}
