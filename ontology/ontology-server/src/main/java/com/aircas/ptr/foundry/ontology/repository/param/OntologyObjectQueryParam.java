package com.aircas.ptr.foundry.ontology.repository.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "实体数据条件查询参数")
public class OntologyObjectQueryParam {

    @ApiModelProperty(value = "本体id", example = "545649a4-8bba-4d0c-b265-362e95fd4ffb", required = true)
    private String ontologyId;

    @ApiModelProperty(value = "页数", required = false, example = "1")
    private Integer page;

    @ApiModelProperty(value = "条数", required = false, example = "10")
    private Integer size;

    @ApiModelProperty(value = "查询条件", required = true, example = "[{\"filterKey\":\"mbbh\",\"filterValue\":7}]")
    private List<FilterParam> filter;
}
