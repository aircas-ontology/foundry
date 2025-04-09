package com.aircas.ptr.foundry.common.util;

import com.alibaba.fastjson.JSONObject;
import com.sun.javafx.collections.MappingChange;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class HttpUtil {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);

    public static String doGet(String url) {

        HttpClient client = new HttpClient();
        // 使用 GET 方法 ，如果服务器需要通过 HTTPS 连接，那只需要将下面 URL 中的 http 换成 https
        HttpMethod method = new GetMethod(url);
        String responseStr = "";
        try {
            client.executeMethod(method);
            // 打印服务器返回的状态
            // logger.info(method.getStatusLine());
            // 打印返回的信息
            responseStr = method.getResponseBodyAsString();
//            logger.info(method.getResponseBodyAsString());
            // 释放连接
            method.releaseConnection();
        } catch (IOException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
            responseStr = "IOException";
        }
        return responseStr;
    }

    /**
     * http调用Post接口，返回结果
     *
     * @param url
     * @param obj 传输对象
     * @return
     * @throws HttpException
     */
    public static String doPost(String url, Object obj) {

        HttpClient client = new HttpClient();
        // 使用POST方法
        PostMethod method = new PostMethod(url);
        RequestEntity re = new StringRequestEntity(JSONObject.toJSONString(obj));
        method.setRequestEntity(re);
        method.addRequestHeader("Content-Type", "application/json; charset=UTF-8");
        String responseStr = "";
        try {
            client.executeMethod(method);
            // 打印服务器返回的状态
            // System.out.println(method.getStatusLine());
            // 打印返回的信息
            responseStr = method.getResponseBodyAsString();
            // System.out.println(responseStr);
            // 释放连接
            method.releaseConnection();
        } catch (IOException e) {
            e.printStackTrace();
            responseStr = "IOException";
        }
        return responseStr;
    }

    /**
     * 发送POST请求，添加表单参数
     *
     * @param url    接口地址
     * @param params 表单参数
     * @return 响应结果
     */
    public static String doPostWithForm(String url, Map<String, String> params) {
        HttpClient client = new HttpClient();
        PostMethod method = new PostMethod(url);

        // 设置表单参数
        NameValuePair[] dataPairs = new NameValuePair[params.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            dataPairs[i++] = new NameValuePair(entry.getKey(), entry.getValue());
        }
        method.setRequestBody(dataPairs);
        // 设置请求头
        method.addRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        try {
            client.executeMethod(method);
            return method.getResponseBodyAsString();
        } catch (IOException e) {
            e.printStackTrace();
            return "IOException";
        } finally {
            method.releaseConnection();
        }
    }

    /**
     * 发送DELETE请求
     *
     * @param url 请求的URL
     * @return 响应结果
     */
    public static boolean doDelete(String url) {
        HttpClient client = new HttpClient();
        DeleteMethod method = new DeleteMethod(url);
        try {
            int statusCode = client.executeMethod(method);
            if (statusCode == 200) {
                return true;
            } else {
                logger.error("HTTP DELETE request failed with status code: {}", statusCode);
                return false;
            }
        } catch (IOException e) {
            logger.error("IOException occurred during HTTP DELETE request", e);
            return false;
        } finally {
            method.releaseConnection();
        }
    }

    public static void main(String[] args) throws HttpException {

    }

}
