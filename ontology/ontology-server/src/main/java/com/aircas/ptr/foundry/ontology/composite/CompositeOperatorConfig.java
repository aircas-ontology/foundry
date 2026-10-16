package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.engine.ScriptEngineRouter;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 编排算子执行相关 Bean。
 *
 * <p>LiteFlow 的节点实例在注册时按节点 id 手工构造，不走 Spring 注入，因此把
 * "用哪个引擎调用算子"收敛成 {@link CompositeFunctionInvoker}，由这里统一装配后交给
 * {@link CompositeChainRegistry}，使脚本函数算子、基础函数算子、外部API函数算子
 * 都能作为编排节点接入工作流。</p>
 */
@Configuration
public class CompositeOperatorConfig {

    @Bean
    public ExternalApiOperator externalApiOperator(ExternalApiExecutor executor, ObjectMapper objectMapper) {
        return new ExternalApiOperator(executor, objectMapper);
    }

    @Bean
    public CompositeFunctionInvoker compositeFunctionInvoker(GroovyService groovyService,
                                                            BasicQueryOperator basicQueryOperator,
                                                            ExternalApiOperator externalApiOperator,
                                                            ScriptEngineRouter scriptEngineRouter) {
        return new CompositeFunctionInvoker(groovyService, basicQueryOperator, externalApiOperator, scriptEngineRouter);
    }
}
