package com.aircas.ptr.foundry.runner.web;

import lombok.Data;

import java.util.Map;

/**
 * handle 执行请求：与 ontology-server 侧 RemoteRunnerScriptEngine 约定一致。
 * {@code input} 为单对象入参（handle(input) 的 input）。
 */
@Data
public class HandleRequest {

    private String functionApi;

    private String version;

    private String code;

    private Map<String, Object> input;

}
