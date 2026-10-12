package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 外部API算子的 HTTP 调用执行器。
 *
 * <p>不复用 {@code common} 模块的 {@code HttpUtil}：那个工具会把非 2xx 与异常统一吞成
 * "远程调用出错"，且不支持自定义请求头，无法给编排节点提供可定位的失败文案。</p>
 */
@Slf4j
@Component
public class ExternalApiExecutor {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** 默认超时秒数 */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** 响应体最大读取长度，避免异常大的报文打满内存 */
    private static final int MAX_BODY_CHARS = 1_000_000;

    /** HTTP 调用结果 */
    public record HttpResult(int status, String body) {

        public boolean isSuccessful() {
            return status >= 200 && status < 300;
        }
    }

    private volatile OkHttpClient client;

    private OkHttpClient client() {
        OkHttpClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = new OkHttpClient.Builder()
                            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .build();
                    client = current;
                }
            }
        }
        return current;
    }

    /**
     * 发起 HTTP 调用。
     *
     * @param url            完整地址（路径占位符已替换、query 已拼接）
     * @param method         请求方法
     * @param headers        请求头
     * @param body           请求体 JSON，null 表示无请求体
     * @param timeoutSeconds 超时秒数，非正数时用默认值
     * @return 状态码与响应体
     */
    public HttpResult call(String url, String method, Map<String, String> headers, String body, int timeoutSeconds) {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> header : safeHeaders(headers)) {
            builder.header(header.getKey(), header.getValue() == null ? "" : header.getValue());
        }
        RequestBody requestBody = body == null
                ? bodyFor(method)
                : RequestBody.create(body, JSON_MEDIA_TYPE);
        builder.method(StringUtils.isBlank(method) ? "GET" : method.trim().toUpperCase(), requestBody);

        OkHttpClient httpClient = timeoutSeconds > 0
                ? client().newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
                : client();

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String text = readBody(response.body());
            return new HttpResult(response.code(), text);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("外部API调用失败: url={}, 原因={}", url, e.getMessage());
            throw new BusinessException("外部API调用失败：" + url + "（" + e.getClass().getSimpleName() + "）",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /** 把 query 参数拼到 url 上 */
    public static String appendQuery(String url, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return url;
        }
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            throw new BusinessException("外部API地址非法：" + url, HttpStatus.BAD_REQUEST);
        }
        HttpUrl.Builder urlBuilder = parsed.newBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (StringUtils.isNotEmpty(entry.getKey())) {
                urlBuilder.addQueryParameter(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return urlBuilder.build().toString();
    }

    /** 校验地址可用（提前给出清晰文案，而不是 OkHttp 的 IllegalArgumentException） */
    public static void requireValidUrl(String url) {
        if (StringUtils.isBlank(url)) {
            throw new BusinessException("外部API地址不能为空", HttpStatus.BAD_REQUEST);
        }
        if (HttpUrl.parse(url) == null) {
            throw new BusinessException("外部API地址非法：" + url, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 无请求体时的兜底。
     *
     * <p>OkHttp 规定 GET/HEAD 必须不带请求体（传非 null 会抛
     * {@code IllegalArgumentException: method GET must not have a request body}），
     * 而 POST/PUT/PATCH 允许空体、DELETE 需要显式空体，因此按方法区分。</p>
     */
    private RequestBody bodyFor(String method) {
        String upper = method == null ? "GET" : method.trim().toUpperCase();
        if ("GET".equals(upper) || "HEAD".equals(upper)) {
            return null;
        }
        return RequestBody.create("{}", JSON_MEDIA_TYPE);
    }

    private String readBody(ResponseBody responseBody) throws Exception {
        if (responseBody == null) {
            return "";
        }
        String text = responseBody.string();
        return text.length() > MAX_BODY_CHARS ? text.substring(0, MAX_BODY_CHARS) : text;
    }

    private static List<Map.Entry<String, String>> safeHeaders(Map<String, String> headers) {
        return headers == null ? List.of() : List.copyOf(headers.entrySet());
    }
}
