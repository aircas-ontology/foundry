package com.aircas.ptr.foundry.ontology.engine;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本引擎路由：收集所有 {@link ScriptEngine} 实现，按 {@link FunctionLanguageEnum} 建立注册表。
 * <p>
 * 后注册的进程内引擎会覆盖同语言的远程 runner；若某语言的 runner 未配置地址且存在进程内引擎，
 * 则用进程内引擎兜底，避免本地/离线环境无法执行算子。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptEngineRouter {

    private final List<ScriptEngine> engines;

    private final Map<FunctionLanguageEnum, ScriptEngine> registry = new EnumMap<>(FunctionLanguageEnum.class);

    /** 语言 -> 进程内引擎（非远程 runner），用于 runner 未配置时兜底 */
    private final Map<FunctionLanguageEnum, ScriptEngine> inProcessEngines = new EnumMap<>(FunctionLanguageEnum.class);

    @PostConstruct
    public void init() {
        for (ScriptEngine engine : engines) {
            for (FunctionLanguageEnum language : engine.supports()) {
                registry.put(language, engine);
                if (!(engine instanceof RemoteRunnerScriptEngine)) {
                    inProcessEngines.put(language, engine);
                }
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
        // 远程 runner 未配置时退回进程内引擎：主应用已有 groovy 编译执行能力，
        // 否则本地/离线环境连一个算子都测不了，而编排里的同类节点其实照常能跑
        if (engine instanceof RemoteRunnerScriptEngine remote && !remote.isConfigured(lang)) {
            ScriptEngine fallback = inProcessEngines.get(lang);
            if (fallback != null) {
                log.info("语言运行时未配置，{} 走进程内引擎兜底：{}", lang.getCode(),
                        fallback.getClass().getSimpleName());
                return fallback;
            }
        }
        return engine;
    }

}
