package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionHandleRuleAddConditionEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import com.aircas.ptr.foundry.ontology.model.enums.RuleMonitorTypeEnum;
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
@Schema(description = "规则判断条件项：两种形态之一。"
        + "① 数值/属性条件：leftOperate 比较符 rightOperate，linkFlag 连接下一条；"
        + "② 监测条件：monitorType 非空时（或规则组 monitorType 已选事件类型时），leftOperate 只给 type=dynamic + OntologyId + storageGroup（可选 entityId 限定实例），"
        + "本次 CDC 事件命中该对象该分组且事件类型一致即为真，rightOperate/condition 不参与求值。"
        + "求值时 AND 优先于 OR，括号可改变优先级；一次 CDC 事件只对应一种监测类型和一个分组，"
        + "多个监测条件之间用 AND 表示同一实例先后发生这些事件（5 分钟累计窗口内凑齐即触发，触发后清空），"
        + "用 OR 表示任一事件发生即触发")
public class RuleJudgeItemParam {

    @Schema(name = "monitorType", description = "监测类型：insert 新增 / update 更新 / delete 删除。"
            + "非空即本条为监测条件（只选对象+分组即可），为空走属性比较条件；"
            + "事件类型已在规则组 monitorType 上选过时本条可省略", example = "insert")
    private RuleMonitorTypeEnum monitorType;

    @Schema(name = "leftOperate", description = "左操作数；监测条件时提供监测对象与分组", required = true)
    private RuleOperateParam leftOperate;

    @Schema(name = "rightOperate", description = "右操作数；监测条件不需要")
    private RuleOperateParam rightOperate;

    @Schema(name = "condition", description = "比较操作符：数字 LT/LE/EQ/NE/GT/GE；字符串 CONTAINS/NOT_CONTAINS/EQ/NE（大小比较仅限数字，不支持 CH）；"
            + "监测条件不需要", example = "GT")
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