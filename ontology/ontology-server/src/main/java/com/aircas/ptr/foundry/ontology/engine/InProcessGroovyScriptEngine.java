package com.aircas.ptr.foundry.ontology.engine;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groovy 进程内引擎：复用 {@link GroovyService} 的编译缓存，按参数表顺序展开调用 handle。
 * <p>
 * Groovy 脚本函数需要引用主程序内部类，因此不再走独立的 groovy-runner 容器，
 * 直接在主 JVM 内编译执行；脚本可访问应用全部类与 Bean。编排（COMPOSITE）节点
 * 也复用同一套进程内执行能力，两端行为一致。</p>
 */
@Component
@RequiredArgsConstructor
public class InProcessGroovyScriptEngine implements ScriptEngine {

    private final GroovyService groovyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Set<FunctionLanguageEnum> supports() {
        return EnumSet.of(FunctionLanguageEnum.GROOVY);
    }

    @Override
    public void validate(ScriptExecutionContext ctx) {
        // parseGroovyCode 会编译源码并查找返回 FunctionResultVO 的 handle 方法，找不到即抛业务异常
        groovyService.parseGroovyCode(ctx.getCode());
    }

    @Override
    public String execute(ScriptExecutionContext ctx) {
        Map<String, Object> paramMap = ctx.getInputMap() == null ? new LinkedHashMap<>() : ctx.getInputMap();
        List<FunctionParamPO> inputParams = ctx.getInputParams();
        Object result = groovyService.invokeHandle(ctx.getFunctionApi(), ctx.getCode(), paramMap, inputParams);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new BusinessException("脚本返回值序列化失败：" + ctx.getFunctionApi());
        }
    }

    @Override
    public void invalidate(FunctionLanguageEnum language, String functionApi) {
        groovyService.invalidateCompiledClass(functionApi);
    }
}
