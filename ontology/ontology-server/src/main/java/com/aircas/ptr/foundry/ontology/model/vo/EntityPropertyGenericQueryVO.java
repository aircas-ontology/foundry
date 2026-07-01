package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(value = "实体属性通用查询VO")
public class EntityPropertyGenericQueryVO {

    @ApiModelProperty(name = "propertyApiName", value = "属性api名称", example = "name")
    private String propertyApiName;

    @ApiModelProperty(name = "propertyDisplayName", value = "属性显示名称", example = "名称")
    private String propertyDisplayName;

    @ApiModelProperty(name = "func", value = "聚合函数名称：SUM, COUNT, AVG, MAX, MIN, DISTINCT, 当查询条件有聚合参数时才填值", example = "SUM")
    private AggFuncEnum aggFunc;

    @ApiModelProperty(name = "alias", value = "聚合函数或属性别名", example = "sum")
    private String alias;

    @ApiModelProperty(name = "propertyValue", value = "返回值", example = "123")
    private Object value;
}
