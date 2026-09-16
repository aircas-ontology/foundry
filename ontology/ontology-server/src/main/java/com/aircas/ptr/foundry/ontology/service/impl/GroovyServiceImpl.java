package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.aspect.FuncParam;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.aircas.ptr.foundry.ontology.utils.SchemaHandleUtil;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroovyServiceImpl implements GroovyService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final static String FUNCTION_RESULT_REFERENCE_NAME = "com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO";

    /**
     * 按 functionApi 缓存。每个条目使用独立 ClassLoader，失效时可关闭，便于 GC 回收 Class（Metaspace）。
     * maximumSize / expireAfterAccess 作为兜底，防止忘记 invalidate 时无限增长。
     */
    private final Cache<String, CompiledScript> compiledScriptCache = CacheBuilder.newBuilder()
            .maximumSize(256)
            .expireAfterAccess(24, TimeUnit.HOURS)
            .removalListener((RemovalNotification<String, CompiledScript> notification) -> {
                CompiledScript value = notification.getValue();
                if (value != null) {
                    value.close();
                }
            })
            .build();

    /**
     * 解析groovy代码块，获取参数列表和返回值信息
     *
     * @param code
     * @return
     */
    @Override
    public List<FunctionParamDTO> parseGroovyCode(String code) {
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(code), "code不能为空");
        // 解析路径不入长期缓存：用完即关 ClassLoader，避免试编译堆积 Metaspace
        CompiledScript compiled = compile(code);
        try {
            Method handleMethod = findHandleMethod(compiled.clazz);
            PreconditionUtils.checkArgument(
                    handleMethod != null && StringUtils.equals(handleMethod.getReturnType().getName(), FUNCTION_RESULT_REFERENCE_NAME),
                    "函数名称handle不存在或者返回类型错误");
            JsonSchemaGenerator generator = new JsonSchemaGenerator(objectMapper);
            Parameter[] parameters = handleMethod.getParameters();
            List<FunctionParamDTO> funcParams = new ArrayList<>();

            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++) {
                    String paramName = getParameterName(parameters[i]);
                    Class<?> paramType = parameters[i].getType();
                    PreconditionUtils.checkArgument(FunctionParamTypeEnum.isBasicType(paramType.getName()), "暂不支持复杂类型参数", HttpStatus.BAD_REQUEST);
                    JsonSchema schema = generator.generateSchema(paramType);
                    String jsonSchema = objectMapper.writeValueAsString(schema);
                    funcParams.add(FunctionParamDTO.builder()
                            .paramName(paramName)
                            .paramType(FunctionParamTypeEnum.getByTypeName(paramType.getName()))
                            .category(FunctionParamCategoryEnum.INPUT)
                            .paramOrder(i + 1)
                            .referenceType(paramType.getName())
                            .paramSchema(jsonSchema)
                            .build());
                }
            }
            JavaType returnType = objectMapper.getTypeFactory().constructType(handleMethod.getGenericReturnType());
            JsonSchema returnSchema = generator.generateSchema(returnType);
            String json = objectMapper.writeValueAsString(returnSchema);
            funcParams.add(FunctionParamDTO.builder()
                    .paramName("result")
                    .paramType(FunctionParamTypeEnum.OBJECT)
                    .category(FunctionParamCategoryEnum.OUTPUT)
                    .paramOrder(1)
                    .referenceType(returnType.getRawClass().getTypeName())
                    .paramSchema(json)
                    .build());
            return funcParams;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("parseGroovyCode failed:", e);
            throw new BusinessException("parseGroovyCode failed");
        } finally {
            compiled.close();
        }
    }


    @SneakyThrows
    @Override
    public String executeGroovy(String functionApi, String code, Map<String, Object> paramMap, List<FunctionParamPO> paramInfos) {
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(functionApi), "functionApi不能为空");
        Class<?> groovyClass = getOrCompileByFunctionApi(functionApi, code);
        GroovyObject groovyInstance = (GroovyObject) groovyClass.getDeclaredConstructor().newInstance();

        var paramValues = paramInfos.stream().map(p -> {
            var value = paramMap.get(p.getParamName());
            if (p.getParamType() == FunctionParamTypeEnum.OBJECT) {
                throw new BusinessException("parse error");
            } else {
                return SchemaHandleUtil.convertValue(value, p.getTypeReferenceName());
            }
        }).collect(Collectors.toList());
        var result = CollectionUtils.isEmpty(paramValues) ?
                groovyInstance.invokeMethod("handle", null) :
                groovyInstance.invokeMethod("handle", paramValues.toArray(new Object[]{}));
        return objectMapper.writeValueAsString(result);
    }

    @Override
    public void invalidateCompiledClass(String functionApi) {
        if (StringUtils.isEmpty(functionApi)) {
            return;
        }
        compiledScriptCache.invalidate(functionApi);
    }

    @PreDestroy
    public void destroy() {
        compiledScriptCache.invalidateAll();
    }

    private String getParameterName(Parameter parameter) {
        FuncParam funcParam = parameter.getAnnotation(FuncParam.class);
        if (funcParam != null) {
            return funcParam.name();
        }
        return parameter.getName();
    }

    /**
     * 按 functionApi 缓存；DB 中 code 变更后 codeHash 不一致则替换并关闭旧 ClassLoader。
     */
    private Class<?> getOrCompileByFunctionApi(String functionApi, String code) {
        String codeHash = cacheKey(code);
        CompiledScript cached = compiledScriptCache.getIfPresent(functionApi);
        if (cached != null && codeHash.equals(cached.codeHash)) {
            return cached.clazz;
        }
        CompiledScript compiled = compile(code);
        // put 替换旧条目时 Guava 会走 removalListener 关闭旧 ClassLoader
        compiledScriptCache.put(functionApi, compiled);
        return compiled.clazz;
    }

    private CompiledScript compile(String code) {
        String codeHash = cacheKey(code);
        GroovyClassLoader loader = createGroovyClassLoader();
        GroovyCodeSource source = new GroovyCodeSource(code, "GroovyFn_" + codeHash + ".groovy", "/groovy/script");
        source.setCachable(false);
        try {
            // parseClass 只返回脚本中第一个类；多 class 脚本需在本次 loader 已加载类中找 handle
            loader.parseClass(source);
            Class<?> handleClass = findHandleClass(loader);
            PreconditionUtils.checkArgument(handleClass != null, "函数handle方法不存在");
            return new CompiledScript(codeHash, handleClass, loader);
        } catch (Exception e) {
            closeQuietly(loader);
            throw e;
        }
    }

    private GroovyClassLoader createGroovyClassLoader() {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setSourceEncoding(StandardCharsets.UTF_8.name());
        return new GroovyClassLoader(GroovyServiceImpl.class.getClassLoader(), config);
    }

    /**
     * 脚本可定义多个类（如 VisibilityHandler + ComputeSateCoveToPoint）。
     * 当前使用独立 ClassLoader，getLoadedClasses 只会看到本次编译结果。
     */
    private Class<?> findHandleClass(GroovyClassLoader loader) {
        Class<?>[] loadedClasses = loader.getLoadedClasses();
        if (loadedClasses == null || loadedClasses.length == 0) {
            return null;
        }
        return Arrays.stream(loadedClasses)
                .filter(clz -> {
                    Method handleMethod = findHandleMethod(clz);
                    return handleMethod != null
                            && StringUtils.equals(handleMethod.getReturnType().getName(), FUNCTION_RESULT_REFERENCE_NAME);
                })
                .findFirst()
                .orElse(null);
    }

    private Method findHandleMethod(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> "handle".equals(m.getName()))
                .findFirst()
                .orElse(null);
    }

    private String cacheKey(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(code.hashCode());
        }
    }

    private static void closeQuietly(GroovyClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.clearCache();
            loader.close();
        } catch (IOException e) {
            // ignore
        }
    }


    /**
     * 一个脚本对应一个独立 ClassLoader，失效后关闭即可让 Class 变为可回收。
     */
    private static final class CompiledScript {
        private final String codeHash;
        private final Class<?> clazz;
        private final GroovyClassLoader classLoader;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private CompiledScript(String codeHash, Class<?> clazz, GroovyClassLoader classLoader) {
            this.codeHash = codeHash;
            this.clazz = clazz;
            this.classLoader = classLoader;
        }

        private void close() {
            if (closed.compareAndSet(false, true)) {
                closeQuietly(classLoader);
            }
        }
    }
}
