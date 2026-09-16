package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "本体属性通用查询请求")
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class EntityPropertyGenericQueryParam extends OntologyIdentifierParam {

    /**
     * 返回属性（包括聚合函数）
     */
    @Schema(required = true, name = "selectProperties", description = "返回属性（包括聚合函数）", example = "[{ \"propertyApiName\": \"id\", \"aggFunc\":\"COUNT\", \"alias\":\"cnt\" } ]")
    @Size(min = 1, message = "至少选择一个属性返回")
    @NotNull(message = "selectProperties is empty")
    private List<OntologySelectPropertyParam> selectProperties;

    /** 过滤树 */
    @Schema(name = "filters", description = "过滤树")
    private FilterGroupParam filters;

    /** 分组 */
    @Schema(name = "groupBy", description = "分组属性", example = "[\"id\",\"name\"]")
    private List<String> groupBy;


    /** 排序 */
    @Schema(name = "orderBy", description = "排序", example = "[{ \"propertyApiName\": \"id\", \"direction\":\"ASC\" } ]")
    private List<OrderByParam> orderBy;

    /** 分页 */
    @Schema(name = "pageNum", description = "分页号，默认1")
    private Integer pageNum = 1;

    @Schema(name = "pageSize", description = "每页条数，默认10")
    private Integer pageSize = 10;

}
