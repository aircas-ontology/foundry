package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "实体属性按行分页查询请求")
public class EntityPropertyRowPageQueryParam extends EntityPropertyRowQueryParam {

    @Schema(name = "storageGroup", description = "属性存储分组名称，与 row_detail 返回的 propertyGroups[].storageGroup 一致", example = "main", required = true)
    @NotBlank(message = "storageGroup is empty")
    private String storageGroup;

    @Schema(name = "pageNum", description = "分页号，默认1", example = "1")
    @Min(value = 1, message = "pageNum must be greater than 0")
    @Builder.Default
    private Integer pageNum = 1;

    @Schema(name = "pageSize", description = "每页条数，默认10", example = "10")
    @Min(value = 1, message = "pageSize must be greater than 0")
    @Max(value = 500, message = "pageSize must be less than or equal to 500")
    @Builder.Default
    private Integer pageSize = 10;

}
