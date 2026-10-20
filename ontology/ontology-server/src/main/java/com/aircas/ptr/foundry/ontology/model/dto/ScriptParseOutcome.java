package com.aircas.ptr.foundry.ontology.model.dto;

import com.aircas.ptr.foundry.ontology.model.enums.ScriptParseStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 单个解析器的产出：状态 + 参数集合 + 可选错误信息。
 * <p>
 * 用于把「语法错误 / 无 handle / runner 未配置 / runner 报错 / 解析异常」与「成功但完全动态」区分开，
 * 逐层透传到 Service，避免异常被静默吞掉后前端只能看到统一的「请手动声明」。
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScriptParseOutcome {

    private final ScriptParseStatus status;
    private final String message;
    private final List<ScriptParsedParam> params;

    public static ScriptParseOutcome ok(List<ScriptParsedParam> params) {
        return new ScriptParseOutcome(ScriptParseStatus.OK, null,
                params == null ? Collections.emptyList() : params);
    }

    public static ScriptParseOutcome of(ScriptParseStatus status, String message) {
        return new ScriptParseOutcome(status, message, Collections.emptyList());
    }
}
