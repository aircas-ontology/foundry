package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "实体属性按行查询请求")
public class EntityPropertyRowQueryParam extends EntityQueryParam {

    @Schema(name = "sort", description = "排序规则：ASC/DESC", example = "DESC", required = true)
    @NotNull(message = "sort is null")
    private QuerySortEnum sort;

}
