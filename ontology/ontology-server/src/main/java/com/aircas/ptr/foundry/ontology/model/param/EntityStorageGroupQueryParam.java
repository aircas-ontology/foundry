package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "存储分组数据查询请求")
public class EntityStorageGroupQueryParam {

    @Schema(name = "ontologyUniqueIdentifier", description = "对象id", example = "ea512d439cd34c6ebbcde6b692261ab5", required = true)
    @NotBlank(message = "ontologyUniqueIdentifier is empty")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;

    @Schema(name = "storageGroup", description = "属性存储分组名称，与 row_detail 返回的 propertyGroups[].storageGroup 一致", example = "guiji", required = true)
    @NotBlank(message = "storageGroup is empty")
    private String storageGroup;

    @Schema(name = "entityPrimaryKey", description = "实体主键值：传了只查该实体在此存储分组下的数据，不传查此存储分组的全部数据", example = "123")
    private Object entityPrimaryKey;

    @JsonAlias("sortby")
    @Schema(name = "sortBy", description = "排序属性，取该存储分组内属性的 apiName 或数据源列名", example = "g_time", required = true)
    @NotBlank(message = "sortBy is empty")
    private String sortBy;

    @Schema(name = "sort", description = "排序规则：ASC/DESC，不传按 DESC", example = "DESC")
    private QuerySortEnum sort;

    @Schema(name = "limit", description = "返回条数，不传返回符合条件的全部数据", example = "1")
    @Min(value = 1, message = "limit must be greater than 0")
    private Integer limit;

    public QuerySortEnum sortOrDefault() {
        return sort == null ? QuerySortEnum.DESC : sort;
    }
}
