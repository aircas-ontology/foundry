package com.aircas.ptr.foundry.common.util;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.filter.LoggingFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HttpUtil {

    private static final MediaType JSON = MediaType.parse("application/json;charset=utf-8");

    private static final OkHttpClient client;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        client = new OkHttpClient().newBuilder()
                .connectTimeout(180, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build();
    }


    public static <T> T postFormData(String url, Map<String, String> params, Map<String, String> formData, String cookie, TypeReference<T> responseType) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (MapUtils.isNotEmpty(params)) {
            params.entrySet().forEach(entry -> urlBuilder.addQueryParameter(entry.getKey(), entry.getValue()));
        }

        FormBody.Builder formBodyBuilder = new FormBody.Builder();
        formData.forEach((k, v) -> {
            if (k != null && v != null) {
                formBodyBuilder.add(k, v);
            }
        });

        Request.Builder requestBuilder = new Request.Builder()
                .header(LoggingFilter.LOG_ID_HEADER, MDC.get(LoggingFilter.LOG_ID_KEY) == null ? IdGenerator.generateLogId() : MDC.get(LoggingFilter.LOG_ID_KEY))
                .post(formBodyBuilder.build())
                .url(urlBuilder.build());

        if (StringUtils.isNotEmpty(cookie)) {
            requestBuilder.addHeader("Cookie", cookie);
        }

        Request request = requestBuilder.build();

        return executeRequest(request, responseType);
    }

    private static String buildFormDataString(Map<String, String> formData) {
        StringBuilder sb = new StringBuilder();
        if (MapUtils.isNotEmpty(formData)) {
            formData.forEach((key, value) -> {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(key).append("=").append(value);
            });
        }
        return sb.toString();
    }


    public static <T> T get(String url, Map<String, String> params, TypeReference<T> responseType) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (MapUtils.isNotEmpty(params)) {
            params.entrySet().forEach(entry -> urlBuilder.addQueryParameter(entry.getKey(), entry.getValue()));
        }

        Request.Builder requestBuilder = new Request.Builder()
                .header(LoggingFilter.LOG_ID_HEADER, MDC.get(LoggingFilter.LOG_ID_KEY) == null ? IdGenerator.generateLogId() : MDC.get(LoggingFilter.LOG_ID_KEY))
                .url(urlBuilder.build());

        Request request = requestBuilder.build();
        return executeRequest(request, responseType);
    }

    public static <T> T postJson(String url, Map<String, String> params, Object jsonBody, TypeReference<T> responseType) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (MapUtils.isNotEmpty(params)) {
            params.entrySet().forEach(entry -> urlBuilder.addQueryParameter(entry.getKey(), entry.getValue()));
        }

        String jsonString = "";
        try {
            jsonString = objectMapper.writeValueAsString(jsonBody);
            log.info("reuest url:" + url + ", request param:" + jsonString);
        } catch (JsonProcessingException e) {
            log.error("json序列化失败", e);
            throw new BusinessException("json序列化失败");
        }

        RequestBody body = RequestBody.create(JSON, jsonString);
        Request.Builder requestBuilder = new Request.Builder()
                .header(LoggingFilter.LOG_ID_HEADER, MDC.get(LoggingFilter.LOG_ID_KEY) == null ? IdGenerator.generateLogId() : MDC.get(LoggingFilter.LOG_ID_KEY))
                .post(body)
                .url(urlBuilder.build());

        Request request = requestBuilder.build();

        return executeRequest(request, responseType);
    }

    public static <T> T putJson(String url, Map<String, String> params, String jsonString, TypeReference<T> responseType) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (MapUtils.isNotEmpty(params)) {
            params.entrySet().forEach(entry -> urlBuilder.addQueryParameter(entry.getKey(), entry.getValue()));
        }

        RequestBody body = RequestBody.create(JSON, jsonString);
        Request.Builder requestBuilder = new Request.Builder()
                .header(LoggingFilter.LOG_ID_HEADER, MDC.get(LoggingFilter.LOG_ID_KEY) == null ? IdGenerator.generateLogId() : MDC.get(LoggingFilter.LOG_ID_KEY))
                .put(body)
                .url(urlBuilder.build());

        Request request = requestBuilder.build();

        return executeRequest(request, responseType);
    }

    public static <T> T deletePathVariable(String url, List<String> pathVariable, TypeReference<T> responseType) {
        url = String.format(url, pathVariable.toArray());
        Request.Builder requestBuilder = new Request.Builder()
                .header(LoggingFilter.LOG_ID_HEADER, MDC.get(LoggingFilter.LOG_ID_KEY) == null ? IdGenerator.generateLogId() : MDC.get(LoggingFilter.LOG_ID_KEY))
                .delete()
                .url(url);
        Request request = requestBuilder.build();
        return executeRequest(request, responseType);
    }


    private static <T> T executeRequest(Request request, TypeReference<T> responseType) {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String respStr = response.body().string();
                throw new BusinessException("unexpected code :" + response + ", response body: " + respStr);
            }
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                String respStr = responseBody.string();
                var res = objectMapper.readValue(respStr, responseType);
                //log.info("resp:" + res);
                return res;
            }
            return null;
        } catch (Exception e) {
            log.error("executeRequest failed", e);
            throw new BusinessException("远程调用出错");
        }
    }


}
