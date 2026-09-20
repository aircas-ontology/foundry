package com.aircas.ptr.foundry.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.TypeReference;
import com.alibaba.fastjson2.util.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 反序列化工具类（基于 fastjson2）
 */
public final class JsonUtil {

    private JsonUtil() {}

    // ==================== 基础反序列化 ====================

    /**
     * 反序列化为指定类型
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }

    /**
     * 反序列化为泛型类型（推荐用于泛型类，如 DebeziumEnvelope<T>）
     */
    public static <T> T parseObject(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, typeRef);
    }

    /**
     * 反序列化为泛型类型，并指定读取特性（如 SupportSmartMatch 用于 snake_case→camelCase 映射）
     */
    public static <T> T parseObject(String json, TypeReference<T> typeRef, JSONReader.Feature... features) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, typeRef.getType(), features);
    }

    /**
     * 反序列化为泛型类型（用 Type 接口，适合动态构造的场景）
     */
    public static <T> T parseObject(String json, Type type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, type);
    }

    // ==================== 集合反序列化 ====================

    /**
     * 反序列化为 List<T>
     */
    public static <T> List<T> parseList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, clazz);
    }

    /**
     * 反序列化为 Map<String, Object>
     */
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        return JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
    }

    // ==================== JSONObject / JSONArray 互转 ====================

    /**
     * 解析为 JSONObject（字段不固定时用）
     */
    public static JSONObject parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json);
    }

    /**
     * 解析为 JSONArray
     */
    public static JSONArray parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseArray(json);
    }

    /**
     * JSONObject 转 Java 对象
     */
    public static <T> T toObject(JSONObject jsonObject, Class<T> clazz) {
        if (jsonObject == null) {
            return null;
        }
        return jsonObject.toJavaObject(clazz);
    }

    /**
     * JSONObject 转泛型对象
     */
    public static <T> T toObject(JSONObject jsonObject, TypeReference<T> typeRef) {
        if (jsonObject == null) {
            return null;
        }
        return jsonObject.to(typeRef);
    }

    /**
     * JSONObject 转泛型对象（支持 JSONReader.Feature）
     */
    public static <T> T toObject(JSONObject jsonObject, TypeReference<T> typeRef, JSONReader.Feature feature) {
        if (jsonObject == null) {
            return null;
        }
        return jsonObject.to(typeRef, feature);
    }

    // ==================== 动态构造泛型 ====================

    /**
     * 构造带泛型参数的 Type，用于 DebeziumEnvelope<OntologyMetaRow> 这种场景
     *
     * <pre>
     * Type type = JsonUtils.parameterizedType(DebeziumEnvelope.class, OntologyMetaRow.class);
     * DebeziumEnvelope&lt;OntologyMetaRow&gt; envelope = JsonUtils.parseObject(json, type);
     * </pre>
     */
    public static Type parameterizedType(Class<?> rawType, Type... actualTypeArguments) {
        return new ParameterizedTypeImpl(actualTypeArguments, null, rawType);
    }

    // ==================== 序列化 ====================

    public static String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.toJSONString(obj);
    }

    public static String toPrettyJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.toJSONString(obj, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
    }
}