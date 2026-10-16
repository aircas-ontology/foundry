package com.aircas.ptr.foundry.ontology.service.parser;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.config.ScriptRunnerProperties;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptParseOutcome;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptParsedParam;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptParseStatus;
import com.aircas.ptr.foundry.ontology.utils.ScriptParamTypeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 远程 Runner 脚本参数解析器：Python/TypeScript 必须在对应语言运行时里做 AST 解析
 * （Python 用 {@code ast} 标准库、TS 用 typescript Compiler API），主应用 HTTP 调 runner 的 {@code /parse}。
 * <p>
 * runner 契约：{@code POST /parse} body={code} → {ok, params:[{name,type,category,source}]} | {ok:false,error}。
 * runner 回传的 type 为语言原生类型文本（str/number/dict/interface 名等），主应用统一经 ScriptParamTypeMapper 归一。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunnerScriptParamParser implements ScriptParamParser {

    private static final String PARSE_PATH = "/parse";

    private final ScriptRunnerProperties properties;

    @Override
    public Set<FunctionLanguageEnum> supports() {
        return Set.of(FunctionLanguageEnum.PYTHON, FunctionLanguageEnum.TYPESCRIPT);
    }

    @SneakyThrows
    @Override
    @SuppressWarnings("unchecked")
    public ScriptParseOutcome parse(FunctionLanguageEnum language, String code) {
        if (!properties.isConfigured(language)) {
            log.warn("语言 runner 未配置，跳过远程参数解析：language={}", language.getCode());
            return ScriptParseOutcome.of(ScriptParseStatus.RUNNER_NOT_CONFIGURED,
                    language.getCode() + " runner 未配置/未部署，无法解析该语言参数");
        }
        String url = properties.baseUrl(language) + PARSE_PATH;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        Map<String, Object> resp;
        try {
            resp = HttpUtil.postJson(url, new HashMap<String, String>(), body,
                    new TypeReference<Map<String, Object>>() {
                    }, properties.getTimeoutMs());
        } catch (BusinessException e) {
            log.warn("调用 runner /parse 失败：language={}, cause={}", language.getCode(), e.getMessage());
            return ScriptParseOutcome.of(ScriptParseStatus.RUNNER_ERROR,
                    "调用 " + language.getCode() + " runner 失败：" + e.getMessage());
        }
        if (resp == null) {
            return ScriptParseOutcome.of(ScriptParseStatus.RUNNER_ERROR, language.getCode() + " runner 无响应");
        }
        if (!Boolean.TRUE.equals(resp.get("ok"))) {
            String err = str(resp.get("error"));
            log.warn("runner /parse 未解析成功：language={}, resp={}", language.getCode(), resp);
            return ScriptParseOutcome.of(ScriptParseStatus.RUNNER_ERROR,
                    err.isEmpty() ? language.getCode() + " runner 解析失败" : err);
        }
        Object rawParams = resp.get("params");
        List<ScriptParsedParam> result = new ArrayList<>();
        if (rawParams instanceof List) {
            for (Object item : (List<?>) rawParams) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> p = (Map<String, Object>) item;
                String category = str(p.get("category"));
                String nativeType = str(p.get("type"));
                result.add(ScriptParsedParam.builder()
                        .name(str(p.get("name")))
                        .type(ScriptParamTypeMapper.map(nativeType))
                        .rawType(nativeType)
                        .category("OUTPUT".equalsIgnoreCase(category)
                                ? FunctionParamCategoryEnum.OUTPUT : FunctionParamCategoryEnum.INPUT)
                        .source(str(p.get("source")))
                        .build());
            }
        }
        return ScriptParseOutcome.ok(result);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
