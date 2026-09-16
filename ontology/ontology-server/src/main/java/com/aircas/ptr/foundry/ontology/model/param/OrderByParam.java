package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.SortEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;


@Schema(description = "排序请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OrderByParam {

    @Schema(name = "propertyApiName", description = "属性apiName",example = "name")
    private String propertyApiName;

    @Schema(name = "sort", description = "排序方向ASC/DESC",example = "DESC")
    private SortEnum sort = SortEnum.ASC;
}
