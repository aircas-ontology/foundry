package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "实体属性通用查询VO")
public class EntityPropertyGenericQueryVO {

    @Schema(name = "propertyApiName", description = "属性api名称", example = "name")
    private String propertyApiName;

    @Schema(name = "propertyDisplayName", description = "属性显示名称", example = "名称")
    private String propertyDisplayName;

    @Schema(name = "func", description = "聚合函数名称：SUM, COUNT, AVG, MAX, MIN, DISTINCT, 当查询条件有聚合参数时才填值", example = "SUM")
    private AggFuncEnum aggFunc;

    @Schema(name = "alias", description = "聚合函数或属性别名", example = "sum")
    private String alias;

    @Schema(name = "propertyValue", description = "返回值", example = "123")
    private Object value;
}
