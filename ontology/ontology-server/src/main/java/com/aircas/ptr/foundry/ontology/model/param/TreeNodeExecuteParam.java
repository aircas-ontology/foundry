package com.aircas.ptr.foundry.ontology.model.param;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个行为节点的执行数据。
 * <p>执行请求按节点标识给这一套值，用于两个节点绑同一个行为、各要不同实例或不同参数的场景。
 * 实例主键、数据位置、关系对端这些字段没给时沿用执行请求里的全树默认值；
 * params（行为外部入参）只认本节点，没有全树默认值可回退，没给的执行前按缺失报错。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树单个行为节点的执行数据")
public class TreeNodeExecuteParam {

    @Schema(name = "entityPrimaryKey",
            description = "本节点用的实例主键；不传沿用执行请求的 entityPrimaryKey", example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "entityPrimaryKeys",
            description = "本节点按对象指定的实例主键（对象标识 -> 实例主键）；不传沿用执行请求的 entityPrimaryKeys",
            example = "{\"satellite\":1,\"groundStation\":2}")
    private Map<String, Object> entityPrimaryKeys;

    @Schema(name = "dataPrimaryKey",
            description = "本节点用的分组表行主键；不传沿用执行请求的 dataPrimaryKey", example = "123")
    private Object dataPrimaryKey;

    @Schema(name = "inputDataPrimaryKeys",
            description = "本节点入参按行读取（对象id -> 存储分组 -> 行主键）；不传沿用执行请求的 inputDataPrimaryKeys",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":{\"weapon_load\":11}}")
    private Map<String, Map<String, Object>> inputDataPrimaryKeys;

    @Schema(name = "outputDataPrimaryKeys",
            description = "本节点出参按行写回（对象id -> 存储分组 -> 行主键）；不传沿用执行请求的 outputDataPrimaryKeys",
            example = "{\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\":{\"duty_log\":12}}")
    private Map<String, Map<String, Object>> outputDataPrimaryKeys;

    @Schema(name = "inputParams",
            description = "本节点按算子入参指定的数据位置（入参名 -> 实例主键 + 分组行主键）；不传沿用执行请求的 inputParams。"
                    + "LIST 入参可以给多个位置，每个位置是列表的一个元素：必须是同一实例、同一分组，各给该分组的一行；"
                    + "其余入参只能一个位置。一个位置时也可以直接写成对象（与旧写法一致）",
            example = "{\"trajectories\":[{\"entityPrimaryKey\":101,\"dataPrimaryKeys\":{\"orbit_point\":11}},"
                    + "{\"entityPrimaryKey\":101,\"dataPrimaryKeys\":{\"orbit_point\":12}}]}")
    @JsonDeserialize(using = InputTargetsDeserializer.class)
    private Map<String, List<ActionInstanceTargetParam>> inputParams;

    @Schema(name = "outputParams",
            description = "本节点按出参指定的写回位置（写回位置标识 -> 实例主键 + 分组行主键）；不传沿用执行请求的 outputParams",
            example = "{\"duty\":{\"entityPrimaryKey\":3,\"dataPrimaryKeys\":{\"duty_log\":12}}}")
    private Map<String, ActionInstanceTargetParam> outputParams;

    @Schema(name = "linkEntityPrimaryKey",
            description = "本节点实例关系行为的对端实体主键；不传沿用执行请求的 linkEntityPrimaryKey", example = "2")
    private Object linkEntityPrimaryKey;

    @Schema(name = "params", description = "本节点绑定的行为的外部输入参数取值（参数名 -> 值）；"
            + "参数名要和行为声明的外部入参一致，声明了的参数必须给，没有全树默认值可回退",
            example = "{\"ammoCount\":80,\"targetId\":77}")
    private Map<String, Object> params;

    /** 每个入参收单个位置或位置数组：单值老写法包成单元素数组，LIST 入参的多位置才有列表元素可言 */
    public static class InputTargetsDeserializer
            extends JsonDeserializer<Map<String, List<ActionInstanceTargetParam>>> {
        @Override
        public Map<String, List<ActionInstanceTargetParam>> deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (!node.isObject()) {
                throw MismatchedInputException.from(p, Map.class,
                        "inputParams 应是入参名到数据位置的映射");
            }
            Map<String, List<ActionInstanceTargetParam>> targets = new LinkedHashMap<>();
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode value = entry.getValue();
                List<ActionInstanceTargetParam> positions = new ArrayList<>();
                if (value.isArray()) {
                    for (JsonNode element : value) {
                        positions.add(p.getCodec().treeToValue(element, ActionInstanceTargetParam.class));
                    }
                } else if (!value.isNull()) {
                    positions.add(p.getCodec().treeToValue(value, ActionInstanceTargetParam.class));
                }
                targets.put(entry.getKey(), positions);
            }
            return targets;
        }
    }
}
