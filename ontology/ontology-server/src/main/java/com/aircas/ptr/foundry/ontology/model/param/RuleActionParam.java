package com.aircas.ptr.foundry.ontology.model.param;

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
@Schema(description = "规则命中后执行的行为参数：行为定位 + 完整执行参数（与行为测试 ActionTestParam 同形式，支持数组批量）")
public class RuleActionParam {

    @JsonProperty("action_api")
    @JsonAlias("actionApi")
    @Schema(name = "action_api", description = "行为 api", example = "actionA", required = true)
    private String actionApi;

    @JsonProperty("ontology_identifier")
    @JsonAlias("ontologyIdentifier")
    @Schema(name = "ontology_identifier", description = "执行目标对象唯一标识", example = "objA")
    private String ontologyIdentifier;

    @JsonProperty("entity_identifiler")
    @JsonAlias({"entity_identifier", "entityIdentifier"})
    @Schema(name = "entity_identifiler", description = "执行目标实例主键；entity_json 未指定 entityPrimaryKey 时用它补上", example = "3")
    private String entityIdentifier;

    @JsonProperty("entity_json")
    @JsonAlias("entityJson")
    @Schema(name = "entity_json", description = "完整行为执行参数的 JSON 字符串（与周期调度 entityJson 同形式，ActionTestParam，支持数组批量）；"
            + "不传时按行为定义构造最小执行参数",
            example = "{\"ontologySpaceId\":156,\"actionApi\":\"actionA\",\"inputParams\":{}}")
    private String entityJson;
}