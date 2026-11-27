package com.aircas.ptr.foundry.ontology.model.param;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 一个算子入参的数据位置，按维度分两种写法：
 * <ul>
 *   <li>一维数组：元素就是数据位置。LIST 入参里它们是这一次执行的那份列表的元素（各元素可以各自指定对象和实例）；
 *       非 LIST 入参里每个元素是一个批量候选值。</li>
 *   <li>二维数组：只在 LIST 入参上用，外层每个元素是一个批量候选，内层是这个候选的整份列表元素。</li>
 * </ul>
 */
@Schema(description = "一个算子入参的数据位置。一维数组：LIST 入参是这次列表的元素、非 LIST 入参每个元素是批量候选；"
        + "二维数组只在 LIST 入参上用，外层是批量候选、内层是该候选的整份列表元素")
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(using = ActionInputPositionsParam.Deserializer.class)
@JsonSerialize(using = ActionInputPositionsParam.Serializer.class)
public class ActionInputPositionsParam {

    @Schema(name = "positions", description = "一维写法：数据位置列表")
    private List<ActionInstanceTargetParam> positions;

    @Schema(name = "candidates", description = "二维写法：批量候选，每个候选是一份完整的列表元素")
    private List<List<ActionInstanceTargetParam>> candidates;

    public static ActionInputPositionsParam ofPositions(List<ActionInstanceTargetParam> positions) {
        return new ActionInputPositionsParam(positions, null);
    }

    public static ActionInputPositionsParam ofCandidates(List<List<ActionInstanceTargetParam>> candidates) {
        return new ActionInputPositionsParam(null, candidates);
    }

    /** 二维写法：外层是批量候选，内层是列表元素 */
    @JsonIgnore
    public boolean isCandidates() {
        return candidates != null;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return candidates != null ? candidates.isEmpty() : positions == null || positions.isEmpty();
    }

    /** 对外仍按请求里的数组形状写回：一维直接是位置数组，二维是候选数组的数组 */
    public static class Serializer extends JsonSerializer<ActionInputPositionsParam> {

        @Override
        public void serialize(ActionInputPositionsParam value, JsonGenerator gen,
                              SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            serializers.defaultSerializeValue(value.isCandidates() ? value.getCandidates() : value.getPositions(), gen);
        }
    }

    public static class Deserializer extends JsonDeserializer<ActionInputPositionsParam> {

        @Override
        public ActionInputPositionsParam deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            var node = (JsonNode) p.getCodec().readTree(p);
            if (node == null || node.isNull()) {
                return null;
            }
            if (!node.isArray()) {
                throw JsonMappingException.from(p, "数据位置要写成数组：LIST 入参的二维写法外层是批量候选、内层是列表元素");
            }
            if (node.isEmpty()) {
                return ofPositions(new ArrayList<>());
            }
            var nested = node.get(0).isArray();
            for (var item : node) {
                if (item.isNull() || item.isArray() != nested) {
                    throw JsonMappingException.from(p, nested
                            ? "二维数据位置里每个候选都必须是数组（一个候选就是一份完整的列表元素）"
                            : "数据位置数组里不能混用对象和数组：LIST 入参要按候选展开请整体写成二维数组");
                }
            }
            if (nested) {
                var candidates = new ArrayList<List<ActionInstanceTargetParam>>(node.size());
                for (var item : node) {
                    var positions = new ArrayList<ActionInstanceTargetParam>(item.size());
                    for (var position : item) {
                        positions.add(position.isNull() ? null
                                : p.getCodec().treeToValue(position, ActionInstanceTargetParam.class));
                    }
                    candidates.add(positions);
                }
                return ofCandidates(candidates);
            }
            var positions = new ArrayList<ActionInstanceTargetParam>(node.size());
            for (var item : node) {
                positions.add(item.isNull() ? null : p.getCodec().treeToValue(item, ActionInstanceTargetParam.class));
            }
            return ofPositions(positions);
        }
    }
}