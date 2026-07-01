package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@ApiModel(description = "本体属性通用查询请求")
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class EntityPropertyGenericQueryParam extends OntologyIdentifierParam {

    /**
     * 返回属性（包括聚合函数）
     */
    @ApiModelProperty(required = true, name = "selectProperties", value = "返回属性（包括聚合函数）", example = "[{ \"propertyApiName\": \"id\", \"aggFunc\":\"COUNT\", \"alias\":\"cnt\" } ]")
    @Size(min = 1, message = "至少选择一个属性返回")
    @NotNull(message = "selectProperties is empty")
    private List<OntologySelectPropertyParam> selectProperties;

    /** 过滤树 */
    @ApiModelProperty(name = "filters", value = "过滤树")
    private FilterGroupParam filters;

    /** 分组 */
    @ApiModelProperty(name = "groupBy", value = "分组属性", example = "[\"id\",\"name\"]")
    private List<String> groupBy;


    /** 排序 */
    @ApiModelProperty(name = "orderBy", value = "排序", example = "[{ \"propertyApiName\": \"id\", \"direction\":\"ASC\" } ]")
    private List<OrderByParam> orderBy;

    /** 分页 */
    @ApiModelProperty(name = "pageNum", value = "分页号，默认1")
    private Integer pageNum = 1;

    @ApiModelProperty(name = "pageSize", value = "每页条数，默认10")
    private Integer pageSize = 10;

}
