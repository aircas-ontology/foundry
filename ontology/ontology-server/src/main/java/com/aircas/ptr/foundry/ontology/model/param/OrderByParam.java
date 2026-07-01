package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.SortEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;


@ApiModel(description = "排序请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OrderByParam {

    @ApiModelProperty(name = "propertyApiName", value = "属性apiName",example = "name")
    private String propertyApiName;

    @ApiModelProperty(name = "sort", value = "排序方向ASC/DESC",example = "DESC")
    private SortEnum sort = SortEnum.ASC;
}
