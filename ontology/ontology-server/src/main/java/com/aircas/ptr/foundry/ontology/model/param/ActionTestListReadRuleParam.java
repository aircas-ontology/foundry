package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.SortEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子表读取规则：本次执行里这个入参读多少行、按哪个属性排序、正序还是倒序。
 * <p>作用范围由 listReadRules 的键（算子入参）决定，对象与存储分组按该入参的绑定行推导。
 * 不配规则的入参沿用原口径：按行主键倒序取最新 10 行。</p>
 */
@Schema(description = "子表读取规则：读取行数与排序方式，作用在 listReadRules 键所指的那个入参上")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionTestListReadRuleParam {

    @Schema(name = "rowCount", description = "该入参本次读取的行数；不传按默认 10 行；"
            + "非列表入参只用到一行，传了会报错", example = "10")
    private Integer rowCount;

    @Schema(name = "orderByProperty", description = "按哪个属性排序，填属性的 apiName，必须是该入参绑定行所在的子表分组；"
            + "不传按行主键排序", example = "create_time")
    private String orderByProperty;

    @Schema(name = "sort", description = "排序方向：ASC 升序 / DESC 降序，默认 DESC（取最新的行）", example = "DESC")
    private SortEnum sort;
}