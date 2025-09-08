package com.aircas.ptr.foundry.ontology.model.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "实体数据条件查询参数")
public class OntologyObjectQueryParam {

    @ApiModelProperty(value = "本体api", example = "mbgjd", required = true)
    private String ontologyApi;

    @ApiModelProperty(value = "页数", required = false, example = "1")
    private Integer page;

    @ApiModelProperty(value = "条数", required = false, example = "10")
    private Integer size;

    @ApiModelProperty(value = "查询条件", required = true, example = "[{\"filterKey\":\"mbbh\",\"filterValue\":342454, \"condition\":\"EQ\"}]")
    private List<FilterParam> filter;

    @ApiModelProperty(value = "排序", required = true, example = "[{\"colum\":\"wzsj\",\"order\":\"DESC\"}]")
    private List<QuerySortParam> sorts;
}
