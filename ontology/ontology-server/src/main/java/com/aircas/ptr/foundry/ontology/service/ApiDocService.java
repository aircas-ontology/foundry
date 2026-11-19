package com.aircas.ptr.foundry.ontology.service;

public interface ApiDocService {

    /**
     * 渲染接口文档 HTML：模板中的 spaceId 与服务地址占位符按入参替换，导出后调用方可直接照抄示例调用。
     *
     * @param spaceId 对象空间 ID
     * @param baseUrl 服务基址（含 context-path，不带结尾斜杠）
     * @return 完整 HTML 文本
     */
    String renderApiDocHtml(Integer spaceId, String baseUrl);
}
