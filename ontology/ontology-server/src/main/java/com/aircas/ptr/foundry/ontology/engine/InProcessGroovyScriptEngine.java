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
 * groovy-runner 是独立容器，本地/离线开发常无该运行时；而编排里的函数节点本来就是进程内执行同一份
 * Groovy 代码。顶层执行若仍走远程 runner，会出现「编排版能跑、单独测试算子报语言运行时未配置」的矛盾，
 * 所以这里覆盖 GROOVY 路由，其余语言继续交给 runner。</p>
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
