package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 此部分代码全部由AI大模型生成
 * 用法：
 * 1.调用parser方法，传入参数类全限定类型，返回参数结构JSON
 * 例：java.lang.String
 * java.lang.Integer
 * com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO
 * java.util.List<com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO>
 * java.util.Map<java.lang.String,com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO>
 * 2.调用resolveJson2Obj方法，传入参数类全限定路径和结构JSON，返回Object对象
 */
@Slf4j
public class SchemaHandleUtil {

    private static final int MAX_DEPTH = 8;
    private static final Set<Class<?>> SIMPLE_TYPES = new HashSet<>(Arrays.asList(
            String.class,
            Boolean.class, boolean.class,
            Byte.class, byte.class,
            Short.class, short.class,
            Integer.class, int.class,
            Long.class, long.class,
            Float.class, float.class,
            Double.class, double.class,
            Character.class, char.class,
            java.math.BigDecimal.class,
            java.math.BigInteger.class,
            java.time.LocalDate.class,
            java.time.LocalDateTime.class,
            java.time.LocalTime.class,
            java.time.OffsetDateTime.class,
            java.time.Instant.class,
            java.util.Date.class
    ));

    public static Class getClassByTypeExpression(String className) {
        try {
            Type type = parseTypeExpression(className);
            return resolveClass(type);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return Object.class;
        }
    }

    /**
     * 传入参数类全限定类型，返回参数结构JSON
     * 例：java.lang.String
     * java.lang.Integer
     * com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO
     * java.util.List<com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO>
     * java.util.Map<java.lang.String,com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO>
     *
     * @param className
     * @return
     */
    public static String parser(String className) {
        try {
            Type root = parseTypeExpression(className);
            Object structure = describeType(root, new ArrayDeque<>(), 0);
            return toJson(structure, 0);
        } catch (ClassNotFoundException e) {
            log.error("parse className error:" + className, e);
            throw new BusinessException("parse className error:" + className);
        }
    }

    private static Object describeType(Type type, Deque<String> visiting, int depth) {
        if (type == null) {
            return "object";
        }
        if (depth > MAX_DEPTH) {
            return "...depth limit reached...";
        }
        String identity = type.getTypeName();
        if (visiting.contains(identity)) {
            return "...circular reference: " + identity + "...";
        }
        visiting.push(identity);
        try {
            if (type instanceof Class<?>) {
                Class<?> clazz = (Class<?>) type;
                if (clazz.isEnum()) {
                    return Arrays.stream(clazz.getEnumConstants())
                            .map(Object::toString)
                            .collect(Collectors.toList());
                }
                if (clazz.isArray()) {
                    List<Object> sample = new ArrayList<>();
                    sample.add(describeType(clazz.getComponentType(), visiting, depth + 1));
                    return sample;
                }
                if (isSimple(clazz)) {
                    return sampleValue(clazz);
                }
                if (Collection.class.isAssignableFrom(clazz)) {
                    List<Object> sample = new ArrayList<>();
                    sample.add(sampleValue(Object.class));
                    return sample;
                }
                if (Map.class.isAssignableFrom(clazz)) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("string", sampleValue(Object.class));
                    return map;
                }
                return describePojo(clazz, visiting, depth);
            }
            if (type instanceof ParameterizedType) {
                return describeParameterizedType((ParameterizedType) type, visiting, depth);
            }
            if (type instanceof GenericArrayType) {
                Type componentType = ((GenericArrayType) type).getGenericComponentType();
                List<Object> sample = new ArrayList<>();
                sample.add(describeType(componentType, visiting, depth + 1));
                return sample;
            }
            if (type instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) type;
                Type[] upperBounds = wildcardType.getUpperBounds();
                return describeType(upperBounds.length > 0 ? upperBounds[0] : Object.class, visiting, depth);
            }
            if (type instanceof TypeVariable<?>) {
                TypeVariable<?> variable = (TypeVariable<?>) type;
                Type[] bounds = variable.getBounds();
                return describeType(bounds.length > 0 ? bounds[0] : Object.class, visiting, depth);
            }
            return type.getTypeName();
        } finally {
            visiting.pop();
        }
    }

    private static Object describeParameterizedType(ParameterizedType parameterizedType,
                                                    Deque<String> visiting,
                                                    int depth) {
        Type raw = parameterizedType.getRawType();
        Type[] arguments = parameterizedType.getActualTypeArguments();
        if (raw instanceof Class<?>) {
            Class<?> rawClass = (Class<?>) raw;
            if (Collection.class.isAssignableFrom(rawClass)) {
                Type elementType = arguments.length > 0 ? arguments[0] : Object.class;
                List<Object> sample = new ArrayList<>();
                sample.add(describeType(elementType, visiting, depth + 1));
                return sample;
            }
            if (Map.class.isAssignableFrom(rawClass)) {
                Type keyType = arguments.length > 0 ? arguments[0] : String.class;
                Type valueType = arguments.length > 1 ? arguments[1] : Object.class;
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put(sampleKeyLabel(keyType), describeType(valueType, visiting, depth + 1));
                return sample;
            }
        }
        return describeType(raw, visiting, depth);
    }

    private static String sampleKeyLabel(Type keyType) {
        Class<?> keyClass = resolveClass(keyType);
        Object sample = sampleValue(keyClass);
        if (sample instanceof String) {
            return (String) sample;
        }
        return String.valueOf(sample);
    }

    private static Class<?> resolveClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            if (raw instanceof Class<?>) {
                return (Class<?>) raw;
            }
        }
        return Object.class;
    }

    private static Map<String, Object> describePojo(Class<?> clazz, Deque<String> visiting, int depth) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Field field : getAllFields(clazz)) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            Type genericType = field.getGenericType();
            fields.put(field.getName(), describeType(genericType, visiting, depth + 1));
        }
        if (fields.isEmpty()) {
            fields.put("_", sampleValue(clazz));
        }
        return fields;
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Collections.addAll(fields, current.getDeclaredFields());
            current = current.getSuperclass();
        }
        return fields;
    }

    private static boolean isSimple(Class<?> clazz) {
        return clazz.isPrimitive()
                || SIMPLE_TYPES.contains(clazz)
                || Number.class.isAssignableFrom(clazz)
                || Date.class.isAssignableFrom(clazz)
                || clazz == Object.class;
    }

    private static Object sampleValue(Class<?> clazz) {
        if (clazz == null) {
            return "object";
        }
        if (clazz == String.class || clazz == Character.class || clazz == char.class) {
            return "string";
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return Boolean.TRUE;
        }
        if (clazz == Long.class || clazz == long.class) {
            return 0L;
        }
        if (clazz == Integer.class || clazz == int.class || clazz == Short.class || clazz == short.class
                || clazz == Byte.class || clazz == byte.class) {
            return 0;
        }
        if (clazz == Float.class || clazz == float.class || clazz == Double.class || clazz == double.class) {
            return 0.0;
        }
        if (Number.class.isAssignableFrom(clazz)) {
            return 0;
        }
        if (Date.class.isAssignableFrom(clazz) || java.time.temporal.Temporal.class.isAssignableFrom(clazz)) {
            return "1970-01-01T00:00:00Z";
        }
        if (clazz == UUID.class) {
            return "00000000-0000-0000-0000-000000000000";
        }
        if (clazz == Object.class) {
            return "string";
        }
        return clazz.getSimpleName();
    }

    private static String toJson(Object value, int indentLevel) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + escape((String) value) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            if (!map.isEmpty()) {
                sb.append("\n");
                Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Object> entry = iterator.next();
                    sb.append(indent(indentLevel + 1))
                            .append("\"").append(escape(entry.getKey())).append("\": ")
                            .append(toJson(entry.getValue(), indentLevel + 1));
                    if (iterator.hasNext()) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append(indent(indentLevel));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            if (!collection.isEmpty()) {
                sb.append("\n");
                Iterator<?> iterator = collection.iterator();
                while (iterator.hasNext()) {
                    Object next = iterator.next();
                    sb.append(indent(indentLevel + 1))
                            .append(toJson(next, indentLevel + 1));
                    if (iterator.hasNext()) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append(indent(indentLevel));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String indent(int level) {
        char[] spaces = new char[level * 2];
        Arrays.fill(spaces, ' ');
        return new String(spaces);
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Type parseTypeExpression(String expression) throws ClassNotFoundException {
        String trimmed = expression.trim();
        int arrayDepth = 0;
        while (trimmed.endsWith("[]")) {
            arrayDepth++;
            trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
        }

        Type base;
        if (trimmed.startsWith("?")) {
            base = parseWildcardType(trimmed);
        } else {
            int genericStart = findTopLevelChar(trimmed, '<');
            if (genericStart < 0) {
                base = parsePrimitiveType(trimmed);
                base = base == null ? Class.forName(trimmed) : base;
            } else {
                String rawName = trimmed.substring(0, genericStart).trim();
                Class<?> rawClass = parsePrimitiveType(rawName);
                rawClass = rawClass == null ? Class.forName(rawName) : rawClass;
                String argsPart = trimmed.substring(genericStart + 1, trimmed.lastIndexOf('>'));
                List<String> argTokens = splitTypeArguments(argsPart);
                Type[] typeArgs = new Type[argTokens.size()];
                for (int i = 0; i < argTokens.size(); i++) {
                    typeArgs[i] = parseTypeExpression(argTokens.get(i));
                }
                base = new SimpleParameterizedType(rawClass, typeArgs, rawClass.getDeclaringClass());
            }
        }

        while (arrayDepth-- > 0) {
            if (base instanceof Class && !((Class<?>) base).isPrimitive()) {
                base = Array.newInstance((Class<?>) base, 0).getClass();
            } else {
                base = new SimpleGenericArrayType(base);
            }
        }
        return base;
    }

    private static Class<?> parsePrimitiveType(String typeName) {
        switch (typeName) {
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "short":
                return short.class;
            case "byte":
                return byte.class;
            case "char":
                return char.class;
            case "boolean":
                return boolean.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            default:
                return null;
        }
    }

    @SneakyThrows
    public static Object convertValue(Object value, String className) {
        if (Objects.nonNull(value)) {
            String valueStr = value.toString();
            Class clazz = getClassByTypeExpression(className);
            if (clazz == Integer.class || clazz == int.class) {
                return Integer.parseInt(valueStr);
            }
            if (clazz == Long.class || clazz == long.class) {
                return Long.parseLong(valueStr);
            }
            if (clazz == Double.class || clazz == double.class) {
                return Double.parseDouble(valueStr);
            }
            if (clazz == Float.class || clazz == float.class) {
                return Float.parseFloat(valueStr);
            }
            if (clazz == Short.class || clazz == short.class) {
                return Short.parseShort(valueStr);
            }
            if (clazz == Character.class || clazz == char.class) {
                return valueStr;
            }
            if (clazz == Byte.class || clazz == byte.class) {
                return Byte.parseByte(valueStr);
            }
            if (clazz == Boolean.class || clazz == boolean.class) {
                return Boolean.parseBoolean(valueStr);
            }
            if (clazz == Date.class) {
                return DateUtils.parseDate(valueStr, "yyyy-MM-dd HH:mm:ss");
            }
            if (clazz == String.class) {
                return valueStr;
            }
        }
        return value;
    }

    private static Type parseWildcardType(String token) throws ClassNotFoundException {
        String trimmed = token.trim();
        if ("?".equals(trimmed)) {
            return new SimpleWildcardType(new Type[]{Object.class}, new Type[0]);
        }
        if (trimmed.startsWith("? extends ")) {
            Type upper = parseTypeExpression(trimmed.substring(10));
            return new SimpleWildcardType(new Type[]{upper}, new Type[0]);
        }
        if (trimmed.startsWith("? super ")) {
            Type lower = parseTypeExpression(trimmed.substring(8));
            return new SimpleWildcardType(new Type[]{Object.class}, new Type[]{lower});
        }
        throw new IllegalArgumentException("Unsupported wildcard type: " + token);
    }

    private static int findTopLevelChar(String text, char target) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == target && depth == 0) {
                return i;
            }
            if (c == '<') {
                depth++;
                continue;
            }
            if (c == '>') {
                depth--;
            }
        }
        return -1;
    }

    private static List<String> splitTypeArguments(String argsPart) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < argsPart.length(); i++) {
            char c = argsPart.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(argsPart.substring(start, i).trim());
                start = i + 1;
            }
        }
        String last = argsPart.substring(start).trim();
        if (!last.isEmpty()) {
            args.add(last);
        }
        return args;
    }

    private static final class SimpleParameterizedType implements ParameterizedType {
        private final Class<?> rawType;
        private final Type[] typeArguments;
        private final Type ownerType;

        private SimpleParameterizedType(Class<?> rawType, Type[] typeArguments, Type ownerType) {
            this.rawType = rawType;
            this.typeArguments = typeArguments;
            this.ownerType = ownerType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return typeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }
    }

    private static final class SimpleGenericArrayType implements GenericArrayType {
        private final Type componentType;

        private SimpleGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }

    private static final class SimpleWildcardType implements WildcardType {
        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        private SimpleWildcardType(Type[] upperBounds, Type[] lowerBounds) {
            this.upperBounds = upperBounds;
            this.lowerBounds = lowerBounds;
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds.clone();
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .findAndRegisterModules();

    public static Object resolveJson2Obj(String typeExpression, String json) {
        try {
            JavaType javaType = MAPPER.getTypeFactory().constructFromCanonical(typeExpression);
            return MAPPER.readValue(json, javaType);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
