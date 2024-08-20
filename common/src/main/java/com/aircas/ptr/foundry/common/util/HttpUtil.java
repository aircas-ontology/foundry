package com.aircas.ptr.foundry.common.util;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class HttpUtil {

	private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);

	public static String doGet(String url) throws HttpException {

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
			logger.info(method.getResponseBodyAsString());
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
	 * @param obj
	 *            传输对象
	 * @return
	 * @throws HttpException
	 */
	public static String doPost(String url, Object obj) throws HttpException {

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

	public static void main(String[] args) throws HttpException {
		String tle1 = JSONObject.parseArray("", JSONObject.class).get(0).getString("tle1");
		String tle2 = JSONObject.parseArray("", JSONObject.class).get(0).getString("tle2");
	}

}
