package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "实体属性按行查询请求")
public class EntityPropertyRowQueryParam extends EntityQueryParam {

    @ApiModelProperty(name = "sort", value = "排序规则：ASC/DESC", example = "DESC", required = true)
    @NotNull(message = "sort is null")
    private QuerySortEnum sort;

}
