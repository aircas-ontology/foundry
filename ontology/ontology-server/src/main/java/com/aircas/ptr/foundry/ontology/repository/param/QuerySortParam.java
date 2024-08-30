package com.aircas.ptr.foundry.ontology.repository.param;

import com.aircas.ptr.foundry.common.constant.QuerySortEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @className: QuerySortParam
 * @author: yangj
 * @date: 2024/8/30 18:11
 * @version: 1.0
 * @description: 实体数据查询排序参数
 */
@ApiModel(description = "实体数据查询排序参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuerySortParam {

    @ApiModelProperty(value = "字段", required = true, example = "wzsj")
    private String colum;

    /**
     * @see
     */
    @ApiModelProperty(value = "升序/降序", required = true, example = "ASC")
    private QuerySortEnum order;

    public String formatOrder() {

        return colum + " " + order.getValue();
    }
}
