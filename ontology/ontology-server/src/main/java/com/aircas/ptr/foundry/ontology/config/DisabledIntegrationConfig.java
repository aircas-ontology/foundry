package com.aircas.ptr.foundry.ontology.config;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityNodeRepository;
import com.aircas.ptr.foundry.ontology.repository.arangodb.EntityRelationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 图数据库（ArangoDB）被关闭时的占位实现。
 *
 * <p>当 foundry.integration.arangodb.enabled=false 时 {@link ArangoConfig} 不生效，
 * 但 EntityServiceImpl 等服务仍以字段方式注入图仓库 Bean，容器必须提供对应实现，否则启动失败。
 * 这里用动态代理补一个桩：只读方法返回空结果，写入方法抛出明确的业务异常，
 * 使图相关能力在本地环境表现为“未启用”，而不影响函数编排等与图无关的能力。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "foundry.integration.arangodb", name = "enabled", havingValue = "false")
public class DisabledIntegrationConfig {

    private static final String DISABLED_MESSAGE = "图数据库(ArangoDB)未启用，该能力不可用";

    private static final List<String> READ_METHOD_PREFIXES =
            List.of("find", "query", "count", "exists", "get", "page");

    @Bean
    @ConditionalOnMissingBean(EntityNodeRepository.class)
    public EntityNodeRepository entityNodeRepositoryStub() {
        return newStub(EntityNodeRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean(EntityRelationRepository.class)
    public EntityRelationRepository entityRelationRepositoryStub() {
        return newStub(EntityRelationRepository.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T newStub(Class<T> repositoryInterface) {
        log.warn("图数据库未启用，注入 {} 的占位实现", repositoryInterface.getSimpleName());
        return (T) Proxy.newProxyInstance(repositoryInterface.getClassLoader(),
                new Class<?>[]{repositoryInterface}, new DisabledRepositoryHandler(repositoryInterface));
    }

    /**
     * 只读且返回集合/标量的方法返回空结果，其余方法抛出业务异常。
     */
    private static class DisabledRepositoryHandler implements InvocationHandler {

        private final Class<?> repositoryInterface;

        DisabledRepositoryHandler(Class<?> repositoryInterface) {
            this.repositoryInterface = repositoryInterface;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> repositoryInterface.getSimpleName() + "#DisabledStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            String name = method.getName();
            if (READ_METHOD_PREFIXES.stream().anyMatch(name::startsWith)) {
                return emptyResult(method);
            }
            throw new BusinessException(DISABLED_MESSAGE + ": " + repositoryInterface.getSimpleName() + "." + name);
        }

        private Object emptyResult(Method method) {
            Class<?> type = method.getReturnType();
            if (Optional.class.equals(type)) {
                return Optional.empty();
            }
            if (Map.class.equals(type)) {
                return Collections.emptyMap();
            }
            if (Collection.class.isAssignableFrom(type)) {
                return Collections.emptyList();
            }
            if (long.class.equals(type) || Long.class.equals(type)) {
                return 0L;
            }
            if (int.class.equals(type) || Integer.class.equals(type)) {
                return 0;
            }
            if (boolean.class.equals(type) || Boolean.class.equals(type)) {
                return false;
            }
            return null;
        }
    }
}
