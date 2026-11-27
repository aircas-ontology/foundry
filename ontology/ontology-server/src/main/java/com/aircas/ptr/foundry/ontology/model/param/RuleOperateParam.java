package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.RuleOperateType;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "规则判断操作数：dynamic 取指定对象（可选实例）的属性值，fixed 用固定值。"
        + "新增/删除条件用 dynamic + OntologyId + 属性定位被判断的对象属性，按事件行取值；"
        + "修改等属性条件用 dynamic 取实例属性当前值")
public class RuleOperateParam {

    @Schema(name = "type", description = "操作数类型：dynamic(变量)|fixed(固定值)", example = "dynamic", required = true)
    private RuleOperateType type;

    @JsonProperty("OntologyId")
    @JsonAlias("ontologyId")
    @Schema(name = "OntologyId", description = "对象唯一标识；type=dynamic 必填", example = "objA")
    private String ontologyId;

    @Schema(name = "entityId", description = "实例对象主键；新增/删除条件可不传（表示该对象任意实例），"
            + "但属性在主表(main)分组时不能指定（事件行即实例本身）；修改等属性条件必填", example = "3")
    private String entityId;

    @Schema(name = "dataPrimaryKey", description = "子表数据行主键；type=dynamic 可选。属性在扩展存储分组时指定取哪一行的值，"
            + "不传沿用原口径（扩展表按主键倒序取最新行）；属性在主表时忽略", example = "12")
    private String dataPrimaryKey;

    @Schema(name = "storageGroup", description = "兼容旧写法：新增/删除条件不再选存储分组，分组由所选属性自动推导，本字段忽略；"
            + "旧数据不带属性只带分组时按对象+分组匹配（仅回显/触发兼容，不建议再传）", example = "main")
    private String storageGroup;

    @Schema(name = "propertyName", description = "属性名（apiName）快照；propertyId 查不到值时按它兜底", example = "longitude")
    private String propertyName;

    @Schema(name = "propertyId", description = "属性唯一标识；新增/删除条件必给（或 propertyName），修改等属性条件与 propertyName 至少给一个，取值优先用它", example = "prop-longitude")
    private String propertyId;

    @Schema(name = "fixValue", description = "固定值；type=fixed 必填", example = "100")
    private String fixValue;
}