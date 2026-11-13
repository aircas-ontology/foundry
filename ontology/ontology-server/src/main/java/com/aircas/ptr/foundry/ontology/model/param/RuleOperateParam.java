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
@Schema(description = "规则判断操作数：dynamic 取指定对象实例的属性当前值，fixed 用固定值")
public class RuleOperateParam {

    @Schema(name = "type", description = "操作数类型：dynamic(变量)|fixed(固定值)", example = "dynamic", required = true)
    private RuleOperateType type;

    @JsonProperty("OntologyId")
    @JsonAlias("ontologyId")
    @Schema(name = "OntologyId", description = "对象唯一标识；type=dynamic 必填", example = "objA")
    private String ontologyId;

    @Schema(name = "entityId", description = "实例对象主键；type=dynamic 必填", example = "3")
    private String entityId;

    @Schema(name = "propertyName", description = "属性名（apiName）快照；propertyId 查不到值时按它兜底", example = "longitude")
    private String propertyName;

    @Schema(name = "propertyId", description = "属性唯一标识；type=dynamic 时与 propertyName 至少给一个，取值优先用它", example = "prop-longitude")
    private String propertyId;

    @Schema(name = "fixValue", description = "固定值；type=fixed 必填", example = "100")
    private String fixValue;
}