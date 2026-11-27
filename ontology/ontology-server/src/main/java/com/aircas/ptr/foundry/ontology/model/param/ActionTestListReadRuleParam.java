package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.SortEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子表读取规则：本次执行里这张子表读多少行、按哪个属性排序、正序还是倒序。
 * <p>作用范围由 listReadRules 的键（子级字段，写成 父参数名.字段路径）决定，命中的绑定行自己带着对象与存储分组；
 * 同一入参下来自不同对象的子表因此可以各配各的规则。不配规则的子表沿用原口径：按行主键倒序取最新 10 行。</p>
 */
@Schema(description = "子表读取规则：读取行数与排序方式，作用在 listReadRules 键所指的那个子级字段所在的子表上")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionTestListReadRuleParam {

    @Schema(name = "rowCount", description = "这条键命中的子表本次读取的行数；不传按默认 10 行；"
            + "非列表入参只用到一行，传了会报错", example = "10")
    private Integer rowCount;

    @Schema(name = "orderByProperty", description = "按哪个属性排序，填属性的 apiName，必须是这条键所在子表上绑过的属性；"
            + "不传按行主键排序", example = "create_time")
    private String orderByProperty;

    @Schema(name = "sort", description = "排序方向：ASC 升序 / DESC 降序，默认 DESC（取最新的行）", example = "DESC")
    private SortEnum sort;
}