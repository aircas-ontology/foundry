package com.aircas.ptr.foundry.common.util;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.filter.LoggingFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.collections4.MapUtils;
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
                .connectTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build();
    }


    public static <T> T get(String url, Map<String, String> params, TypeReference<T> responseType) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (MapUtils.isNotEmpty(params)) {
            params.entrySet().forEach(entry -> urlBuilder.addQueryParameter(entry.getKey(), entry.getValue()));
        }

        Request.Builder requestBuilder = new Request.Builder()
                .header(LoggingFilter.LOG_ID_HEADER, MDC.get(LoggingFilter.LOG_ID_KEY))
                .url(urlBuilder.build());

        Request request = requestBuilder.build();

        return executeRequest(request, responseType);
    }

    public static <T> T postJson(String url, Map<String, String> params, String jsonString, TypeReference<T> responseType) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (MapUtils.isNotEmpty(params)) {
            params.entrySet().forEach(entry -> urlBuilder.addQueryParameter(entry.getKey(), entry.getValue()));
        }

        RequestBody body = RequestBody.create(JSON, jsonString);
        Request.Builder requestBuilder = new Request.Builder()
                .header(LoggingFilter.LOG_ID_HEADER, MDC.get(LoggingFilter.LOG_ID_KEY))
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
                .header(LoggingFilter.LOG_ID_HEADER, MDC.get(LoggingFilter.LOG_ID_KEY))
                .put(body)
                .url(urlBuilder.build());

        Request request = requestBuilder.build();

        return executeRequest(request, responseType);
    }

    public static <T> T deletePathVariable(String url, List<String> pathVariable, TypeReference<T> responseType) {
        url = String.format(url, pathVariable);
        Request.Builder requestBuilder = new Request.Builder()
                .header(LoggingFilter.LOG_ID_HEADER, MDC.get(LoggingFilter.LOG_ID_KEY))
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
                return objectMapper.readValue(respStr, responseType);
            }
            return null;
        } catch (Exception e) {
            log.error("executeRequest failed", e);
            throw new BusinessException("远程调用出错");
        }
    }


}
