package com.aircas.ptr.foundry.ontology.engine;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本引擎路由：收集所有 {@link ScriptEngine} 实现，按 {@link FunctionLanguageEnum} 建立注册表。
 * <p>
 * 当前仅有 {@link RemoteRunnerScriptEngine}（支持全部语言）；后续新增进程内引擎时按语言覆盖即可，调用方无感。
 */
@Component
@RequiredArgsConstructor
public class ScriptEngineRouter {

    private final List<ScriptEngine> engines;

    private final Map<FunctionLanguageEnum, ScriptEngine> registry = new EnumMap<>(FunctionLanguageEnum.class);

    @PostConstruct
    public void init() {
        for (ScriptEngine engine : engines) {
            for (FunctionLanguageEnum language : engine.supports()) {
                registry.put(language, engine);
            }
        }
    }

    /**
     * 按语言取引擎；语言为空按 GROOVY 兜底（兼容历史无 language 的记录），无对应引擎则抛业务异常。
     */
    public ScriptEngine route(FunctionLanguageEnum language) {
        FunctionLanguageEnum lang = language == null ? FunctionLanguageEnum.GROOVY : language;
        ScriptEngine engine = registry.get(lang);
        if (engine == null) {
            throw new BusinessException("不支持的脚本语言：" + lang);
        }
        return engine;
    }

}
