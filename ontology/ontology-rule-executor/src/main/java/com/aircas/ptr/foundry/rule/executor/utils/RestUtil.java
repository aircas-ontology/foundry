package com.aircas.ptr.foundry.rule.executor.utils;

import com.alibaba.fastjson.JSONObject;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.ParameterizedType;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Spring RestTemplate 工具类
 *
 * @author diva
 */
public final class RestUtil {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36";
    private static SimpleClientHttpRequestFactory requestFactory = createFactory();

    private static SimpleClientHttpRequestFactory createFactory() {
        requestFactory = new SimpleClientHttpRequestFactory();
        // 连接超时时间
        requestFactory.setConnectTimeout(10000);
        // 读取超时时间
        requestFactory.setReadTimeout(10000);
        return requestFactory;
    }

    private RestUtil() {
    }

    public static HttpHeaders getHttpHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.add("user-agent", USER_AGENT);
        return headers;
    }

    /**
     * GET 请求
     *
     * @param url
     * @param clazz
     * @param <T>
     * @return
     */
    public static <T> T get(String url, Class<T> clazz) {
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        HttpEntity<String> entity = new HttpEntity<>("parameters", getHttpHeader());
        T result = restTemplate.exchange(url, HttpMethod.GET, entity, clazz).getBody();
        return result;
    }

    public static <T> T get(String url, ParameterizedTypeReference<T> type) {
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        HttpEntity<String> entity = new HttpEntity<>("parameters", getHttpHeader());
        T result = restTemplate.exchange(url, HttpMethod.GET, entity, type).getBody();
        return result;
    }

    /**
     * post 请求
     *
     * @param url
     * @param bean  POST 参数
     * @param clazz
     * @param <T>   返回值
     * @return
     */
    public static <T> T post(String url, Object bean, Class<T> clazz) {
        HttpHeaders httpHeader = getHttpHeader();
        // 表单提交方式
        httpHeader.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(JSONObject.toJSONString(bean), httpHeader);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        T result = restTemplate.exchange(url, HttpMethod.POST, requestEntity, clazz).getBody();
        return result;
    }


    public static <T> T post(String url, Object bean, ParameterizedTypeReference<T> type) {
        HttpHeaders httpHeader = getHttpHeader();
        // 表单提交方式
        httpHeader.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(JSONObject.toJSONString(bean), httpHeader);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        T result = restTemplate.exchange(url, HttpMethod.POST, requestEntity, type).getBody();
        return result;
    }

    public static Object post(String url, String bean) {
        HttpHeaders httpHeader = getHttpHeader();
        // 表单提交方式
        HttpEntity<String> requestEntity = new HttpEntity<>(bean, httpHeader);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getMessageConverters().set(1,new StringHttpMessageConverter(Charset.forName("UTF-8")));
        Object result = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class).getBody();
        return result;
    }

    /**
     * post 请求
     *
     * @param url
     * @param bean  POST 参数
     * @param clazz
     * @param <T>   返回值
     * @return
     */
    public static <T> T post(String url,HttpHeaders httpHeader,Object bean, Class<T> clazz) {
//        HttpHeaders httpHeader = getHttpHeader();
//        // 表单提交方式
//        httpHeader.setContentType(MediaType.APPLICATION_JSON);
//        httpHeader.add("appect-charset",charset);
        HttpEntity<String> requestEntity = new HttpEntity<>(JSONObject.toJSONString(bean), httpHeader);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        T result = restTemplate.exchange(url, HttpMethod.POST, requestEntity, clazz).getBody();
        return result;
    }

    /**
     * post 请求
     *
     * @param url
     * @param clazz
     * @param form  Form 参数
     * @param <T>   返回值
     * @return
     */
    public static <T> T postForm(String url, HttpHeaders httpHeader,Class<T> clazz, MultiValueMap<String, Object> form) {
//        HttpHeaders httpHeader = getHttpHeader();
//        // 表单提交方式
//        httpHeader.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(form, httpHeader);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        T result = restTemplate.exchange(url, HttpMethod.POST, requestEntity, clazz).getBody();
        return result;
    }

//    /**
//     * post 请求
//     *
//     * @param url
//     * @param clazz
//     * @param dataList  Form 参数
//     * @param <T>   返回值
//     * @return
//     */
//    public static <T> T postFormList(String url, Class<T> clazz, ArrayList dataList) {
//        HttpHeaders httpHeader = getHttpHeader();
//        // 表单提交方式
////        httpHeader.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//        httpHeader.add("Content-Type",MediaType.APPLICATION_JSON_UTF8_VALUE);
//        HttpEntity<ArrayList> requestEntity = new HttpEntity<>(dataList, httpHeader);
//
//        RestTemplate restTemplate = new RestTemplate(requestFactory);
//        T result = restTemplate.exchange(url, HttpMethod.POST, requestEntity, clazz).getBody();
//        return result;
//    }
}
