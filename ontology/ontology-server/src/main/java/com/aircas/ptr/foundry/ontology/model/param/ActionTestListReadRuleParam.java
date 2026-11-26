package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.SortEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对象模式下子表的读取规则：遍历到的实例读多少行子表、按哪个属性、正序还是倒序。
 * <p>不配规则的分组沿用原口径：按行主键倒序取最新 10 行。</p>
 */
@Schema(description = "对象模式下的子表读取规则：按对象（可细化到存储分组）指定读取行数与排序方式")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionTestListReadRuleParam {

    @Schema(name = "ontologyUniqueIdentifier", description = "规则作用的对象id；必须是本次遍历到的对象", required = true, example = "3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34")
    private String ontologyUniqueIdentifier;

    @Schema(name = "storageGroup", description = "规则作用的存储分组（子表分组名）；不传对该对象下的所有子表分组生效，"
            + "此时排序属性只在其所属分组生效，其余分组仍按行主键倒序", example = "weapon_load")
    private String storageGroup;

    @Schema(name = "rowCount", description = "该分组读取的行数；不传按默认 10 行", example = "10")
    private Integer rowCount;

    @Schema(name = "orderByProperty", description = "按哪个属性排序，填属性的 apiName；不传按行主键排序", example = "create_time")
    private String orderByProperty;

    @Schema(name = "sort", description = "排序方向：ASC 升序 / DESC 降序，默认 DESC（取最新的行）", example = "DESC")
    private SortEnum sort;
}