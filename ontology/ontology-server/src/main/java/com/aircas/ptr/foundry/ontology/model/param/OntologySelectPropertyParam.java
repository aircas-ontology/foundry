package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;



@Schema(description = "需要返回的属性请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySelectPropertyParam {

    @Schema(name = "propertyApiName", description = "属性api名称，该参数必填", example = "id")
    private String propertyApiName;      // "*" 允许 COUNT(*)

    @Schema(name = "func", description = "聚合函数名称：SUM, COUNT, AVG, MAX, MIN, DISTINCT，当需要聚合属性时才传递该参数，没有聚合属性不传", example = "SUM")
    private AggFuncEnum aggFunc;

    @Schema(name = "alias", description = "聚合函数或属性别名", example = "average")
    private String alias;

}
