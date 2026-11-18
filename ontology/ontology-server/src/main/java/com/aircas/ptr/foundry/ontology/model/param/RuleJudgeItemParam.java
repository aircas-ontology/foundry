package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionHandleRuleAddConditionEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "规则判断条件项：左操作数 比较符 右操作数，可带括号，linkFlag 连接下一条。"
        + "求值时 AND 优先于 OR，括号可改变优先级")
public class RuleJudgeItemParam {

    @Schema(name = "leftOperate", description = "左操作数", required = true)
    private RuleOperateParam leftOperate;

    @Schema(name = "rightOperate", description = "右操作数", required = true)
    private RuleOperateParam rightOperate;

    @Schema(name = "condition", description = "比较操作符：数字 LT/LE/EQ/NE/GT/GE；字符串 CONTAINS/NOT_CONTAINS/EQ/NE（大小比较仅限数字，不支持 CH）", example = "GT", required = true)
    private ActionHandleRuleAddConditionEnum condition;

    @Schema(name = "isLeft", description = "本条件前左括号个数，支持嵌套如 (( 传 2；兼容旧布尔传法 true=1/false=0", example = "2")
    @JsonDeserialize(using = BracketCountDeserializer.class)
    private Integer isLeft;

    @Schema(name = "isRight", description = "本条件后右括号个数，支持嵌套如 )) 传 2；兼容旧布尔传法 true=1/false=0", example = "2")
    @JsonDeserialize(using = BracketCountDeserializer.class)
    private Integer isRight;

    @Schema(name = "linkFlag", description = "与下一条的连接符 AND/OR；最后一条不传", example = "AND")
    private ActionRuleConnectType linkFlag;

    /** 括号数量：数字按个数，布尔 true=1/false=0，兼容前端旧传法 */
    public static class BracketCountDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken t = p.currentToken();
            if (t == JsonToken.VALUE_TRUE) {
                return 1;
            }
            if (t == JsonToken.VALUE_FALSE) {
                return 0;
            }
            if (t == JsonToken.VALUE_NUMBER_INT) {
                return p.getIntValue();
            }
            if (t == JsonToken.VALUE_STRING) {
                String s = p.getText().trim();
                if ("true".equalsIgnoreCase(s)) {
                    return 1;
                }
                if ("false".equalsIgnoreCase(s)) {
                    return 0;
                }
                return Integer.valueOf(s);
            }
            return null;
        }
    }
}