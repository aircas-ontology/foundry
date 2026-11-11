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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroovyServiceImpl implements GroovyService {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            // 返回类型不作限制：脚本可返回工程内 VO、脚本内自定义类或标量，
            // 下游统一按「有 data 键取 data，没有则原样透传」消费
            PreconditionUtils.checkNotNull(handleMethod, "函数名称handle不存在");
            JsonSchemaGenerator generator = new JsonSchemaGenerator(objectMapper);
            Parameter[] parameters = handleMethod.getParameters();
            List<FunctionParamDTO> funcParams = new ArrayList<>();

            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++) {
                    String paramName = getParameterName(parameters[i]);
                    Class<?> paramType = parameters[i].getType();
                    FunctionParamTypeEnum paramTypeEnum = FunctionParamTypeEnum.getByTypeName(paramType.getName());
                    // 允许基本类型 + OBJECT/LIST（Map/Object/List 等容器类型）
                    PreconditionUtils.checkArgument(
                            paramTypeEnum != null || 
                            paramType.getName().equals("java.lang.Object") ||
                            paramType.getName().equals("java.util.Map") ||
                            paramType.getName().equals("java.util.List") ||
                            paramType.isAssignableFrom(Map.class) ||
                            paramType.isAssignableFrom(List.class),
                            "暂不支持复杂类型参数", HttpStatus.BAD_REQUEST);
                    JsonSchema schema = generator.generateSchema(paramType);
                    String jsonSchema = objectMapper.writeValueAsString(schema);
                    funcParams.add(FunctionParamDTO.builder()
                            .paramName(paramName)
                            .paramType(paramTypeEnum != null ? paramTypeEnum : 
                                      (paramType.isAssignableFrom(List.class) ? FunctionParamTypeEnum.LIST : FunctionParamTypeEnum.OBJECT))
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
            Class<?> returnClass = returnType.getRawClass();
            FunctionParamTypeEnum returnParamType = FunctionParamTypeEnum.getByTypeName(returnClass.getName());
            // 与入参同一口径：标量按类型落，List 落 LIST，其余（FunctionResultVO、自定义类）落 OBJECT
            if (returnParamType == null && returnClass.isAssignableFrom(List.class)) {
                returnParamType = FunctionParamTypeEnum.LIST;
            }
            if (returnParamType == null) {
                returnParamType = FunctionParamTypeEnum.OBJECT;
            }
            funcParams.add(FunctionParamDTO.builder()
                    .paramName("result")
                    .paramType(returnParamType)
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
            // OBJECT/LIST 类型直接透传（编排上游节点产出的对象/列表），其余按声明类型转换
            if (p.getParamType() != FunctionParamTypeEnum.OBJECT
                    && p.getParamType() != FunctionParamTypeEnum.LIST) {
                return convertScriptParam(p, value, () -> SchemaHandleUtil.convertValue(value, p.getTypeReferenceName()));
            }
            return value;
        }).collect(Collectors.toList());
        var result = CollectionUtils.isEmpty(paramValues) ?
                groovyInstance.invokeMethod("handle", null) :
                groovyInstance.invokeMethod("handle", paramValues.toArray(new Object[]{}));
        return objectMapper.writeValueAsString(result);
    }

    @SneakyThrows
    @Override
    public Object invokeHandle(String functionApi, String code, Map<String, Object> paramMap,
                               List<FunctionParamPO> paramInfos) {
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(functionApi), "functionApi不能为空");
        Class<?> groovyClass = getOrCompileByFunctionApi(functionApi, code);
        GroovyObject groovyInstance = (GroovyObject) groovyClass.getDeclaredConstructor().newInstance();
        Map<String, Object> inputMap = paramMap == null ? new HashMap<>() : paramMap;
        List<Object> paramValues = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(paramInfos)) {
            for (FunctionParamPO paramInfo : paramInfos) {
                Object value = inputMap.get(paramInfo.getParamName());
                // 编排上游节点产出的对象/列表直接透传，其余按声明类型转换，与 executeGroovy 保持一致
                // typeReferenceName 为空时，对于基本类型按 paramType 转换，其他类型直接透传
                if (paramInfo.getParamType() != FunctionParamTypeEnum.OBJECT
                        && paramInfo.getParamType() != FunctionParamTypeEnum.LIST) {
                    var rawValue = value;
                    value = convertScriptParam(paramInfo, rawValue, () -> convertByDeclaredType(paramInfo, rawValue));
                }
                paramValues.add(value);
            }
        }
        return paramValues.isEmpty()
                ? groovyInstance.invokeMethod("handle", null)
                : groovyInstance.invokeMethod("handle", paramValues.toArray(new Object[]{}));
    }

    /**
     * 按参数声明类型转换脚本入参，转换失败时报出是哪个入参、期望什么类型、收到了什么。
     * 原先 NumberFormatException 直接冒到全局异常处理器，接口只回 500「业务异常」，
     * 传错值和算子内部报错看不出区别；值能正常转换时与改造前完全一致。
     */
    private Object convertScriptParam(FunctionParamPO paramInfo, Object value, Supplier<Object> converter) {
        try {
            return converter.get();
        } catch (RuntimeException e) {
            var expected = StringUtils.isNotEmpty(paramInfo.getTypeReferenceName())
                    ? paramInfo.getTypeReferenceName() : paramInfo.getParamType().getVale();
            throw new BusinessException("算子入参[" + paramInfo.getParamName() + "]类型转换失败：期望 "
                    + expected + "，收到的值是 " + value, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 有 Java 类名时按类名转；没有类名时按参数类型做窄化或解析，
     * 命中不到的类型（如 STRING、OBJECT 以外的自定义类）保持原值，与逐分支改造前一致。
     */
    private Object convertByDeclaredType(FunctionParamPO paramInfo, Object value) {
        if (StringUtils.isNotEmpty(paramInfo.getTypeReferenceName())) {
            return SchemaHandleUtil.convertValue(value, paramInfo.getTypeReferenceName());
        }
        if (value instanceof Number number) {
            // 上游产出的数值类型与下游声明不一致时做窄化/宽化
            return switch (paramInfo.getParamType()) {
                case INTEGER -> number.intValue();
                case DOUBLE -> number.doubleValue();
                case LONG -> number.longValue();
                case FLOAT -> number.floatValue();
                default -> value;
            };
        }
        if (value == null) {
            return null;
        }
        // typeReferenceName 为空但参数是基本类型，按 paramType 转换
        String valueStr = value.toString();
        return switch (paramInfo.getParamType()) {
            // 兼容浮点数形式的字符串（如 "100.0"）
            case INTEGER -> valueStr.contains(".") ? (int) Double.parseDouble(valueStr) : Integer.parseInt(valueStr);
            case LONG -> valueStr.contains(".") ? (long) Double.parseDouble(valueStr) : Long.parseLong(valueStr);
            case DOUBLE -> Double.parseDouble(valueStr);
            case BOOL -> Boolean.parseBoolean(valueStr);
            default -> value;
        };
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

    /**
     * 编译 Groovy 源码，在已加载类中查找含 handle 方法的类（返回类型不限）。
     * 用于创建时反射解析参数（parseGroovyCode）、脚本函数（CUSTOMIZE）的进程内执行
     * （{@link com.aircas.ptr.foundry.ontology.engine.InProcessGroovyScriptEngine}）以及函数编排节点的进程内执行。
     */
    private CompiledScript compile(String code) {
        String codeHash = cacheKey(code);
        GroovyClassLoader loader = createGroovyClassLoader();
        GroovyCodeSource source = new GroovyCodeSource(code, "GroovyFn_" + codeHash + ".groovy", "/groovy/script");
        source.setCachable(false);
        try {
            // parseClass 只返回脚本中第一个类；多 class 脚本需在本次 loader 已加载类中找 handle
            Class<?> primaryClass = loader.parseClass(source);
            Class<?> handleClass = findHandleClass(primaryClass, loader, code);
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
     * 脚本可定义多个类（如 VisibilityHandler + ComputeSateCoveToPoint），入口类只按「声明了 handle」认定。
     * 多个类都声明 handle 时取源码里 class 声明最靠前的那个，
     * 与 {@code GroovyScriptParamParser} 静态解析选 handle 的顺序一致，避免解析用 A 类、执行用 B 类。
     * 当前使用独立 ClassLoader，getLoadedClasses 只会看到本次编译结果。
     */
    private Class<?> findHandleClass(Class<?> primaryClass, GroovyClassLoader loader, String code) {
        if (primaryClass != null && findHandleMethod(primaryClass) != null) {
            return primaryClass;
        }
        Class<?>[] loadedClasses = loader.getLoadedClasses();
        if (loadedClasses == null || loadedClasses.length == 0) {
            return null;
        }
        return Arrays.stream(loadedClasses)
                .filter(clz -> findHandleMethod(clz) != null)
                .min(Comparator.comparingInt((Class<?> clz) -> declarationIndex(code, clz)))
                .orElse(null);
    }

    /** 类声明在源码中的位置；脚本生成类、内部类等匹配不到的排在最后。 */
    private static int declarationIndex(String code, Class<?> clazz) {
        String className = clazz.getName();
        int dot = className.lastIndexOf('.');
        String simpleName = dot >= 0 ? className.substring(dot + 1) : className;
        Matcher matcher = Pattern.compile("\\bclass\\s+" + Pattern.quote(simpleName) + "\\b").matcher(code);
        return matcher.find() ? matcher.start() : Integer.MAX_VALUE;
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
