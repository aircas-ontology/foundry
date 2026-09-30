package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "实例数据属性查询请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class EntityInstanceDataQueryParam {

    @NotNull(message = "spaceId is null")
    @SpaceIdVerify
    @Schema(name = "spaceId", description = "本体空间id", example = "1", required = true)
    private Integer spaceId;

    @NotBlank(message = "ontologyUniqueIdentifier is empty")
    @OntologyIdVerify
    @Schema(name = "ontologyUniqueIdentifier", description = "本体对象唯一标识", example = "abcdef", required = true)
    private String ontologyUniqueIdentifier;

    @NotEmpty(message = "propertyApiNames is empty")
    @Schema(name = "propertyApiNames", description = "需要查询的属性apiName列表", example = "[\"name\", \"age\", \"time\"]", required = true)
    private List<String> propertyApiNames;

    @Schema(name = "filters", description = "过滤条件（选填），支持嵌套 AND/OR；条件中的属性必须是该本体的属性，值类型需与属性类型兼容")
    private FilterGroupParam filters;

    @Schema(name = "pageNum", description = "页码，默认1")
    private Integer pageNum = 1;

    @Schema(name = "pageSize", description = "每页条数，默认10")
    private Integer pageSize = 10;
}
