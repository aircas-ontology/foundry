package com.aircas.ptr.foundry.ontology.engine;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.config.ScriptRunnerProperties;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 远程 Runner 脚本引擎：四种语言统一通过 HTTP 调用各自的语言 runner 容器执行。
 * <p>
 * runner 对外契约（各语言镜像一致）：
 * <ul>
 *   <li>{@code POST /functions/handle} body={functionApi,version,code,input} → FunctionResultVO 形态 + {@code _meta}</li>
 *   <li>{@code POST /validate} body={code} → {ok, error}</li>
 * </ul>
 * 主应用不编译/执行任何用户代码，故障隔离由 runner 容器承担。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteRunnerScriptEngine implements ScriptEngine {

    private static final String HANDLE_PATH = "/functions/handle";
    private static final String VALIDATE_PATH = "/validate";
    private static final String META_KEY = "_meta";

    private final ScriptRunnerProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Set<FunctionLanguageEnum> supports() {
        return EnumSet.allOf(FunctionLanguageEnum.class);
    }

    /**
     * 该语言的 runner 基地址是否已配置。未配置时 {@link ScriptEngineRouter} 才会考虑进程内兜底引擎，
     * 避免把「runner 没配」与「脚本执行失败」混成同一个报错。
     */
    public boolean isConfigured(FunctionLanguageEnum language) {
        return properties.isConfigured(language);
    }

    @SneakyThrows
    @Override
    public String execute(ScriptExecutionContext ctx) {
        String url = properties.baseUrl(ctx.getLanguage()) + HANDLE_PATH;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("functionApi", ctx.getFunctionApi());
        body.put("version", ctx.getVersion());
        body.put("code", ctx.getCode());
        body.put("input", parseInput(ctx.getInputJson()));

        Map<String, Object> resp = HttpUtil.postJson(url, new HashMap<String, String>(), body,
                new TypeReference<Map<String, Object>>() {
                }, properties.getTimeoutMs());
        if (resp == null) {
            throw new BusinessException("语言运行时返回为空：" + ctx.getLanguage().getCode());
        }
        // runner 可能在结果外附带 _meta（耗时/状态/缓存哈希），剥离后仅保留 FunctionResultVO 形态字段
        resp.remove(META_KEY);
        return objectMapper.writeValueAsString(resp);
    }

    @SneakyThrows
    @Override
    public void validate(ScriptExecutionContext ctx) {
        String url = properties.baseUrl(ctx.getLanguage()) + VALIDATE_PATH;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ctx.getCode());

        Map<String, Object> resp = HttpUtil.postJson(url, new HashMap<String, String>(), body,
                new TypeReference<Map<String, Object>>() {
                }, properties.getTimeoutMs());
        boolean ok = resp != null && Boolean.TRUE.equals(resp.get("ok"));
        if (!ok) {
            Object error = resp == null ? "无响应" : resp.get("error");
            throw new BusinessException("脚本校验失败：" + error);
        }
    }

    @Override
    public void invalidate(FunctionLanguageEnum language, String functionApi) {
        // runner 侧按 functionApi+codeHash 自动失效编译产物，主应用无需主动通知；保留钩子以备后续强失效。
        log.debug("invalidate 交由 runner 自动处理，language={}, api={}", language, functionApi);
    }

    @SneakyThrows
    private Object parseInput(String inputJson) {
        if (inputJson == null || inputJson.isBlank()) {
            return new LinkedHashMap<String, Object>();
        }
        return objectMapper.readValue(inputJson, new TypeReference<Map<String, Object>>() {
        });
    }

}
