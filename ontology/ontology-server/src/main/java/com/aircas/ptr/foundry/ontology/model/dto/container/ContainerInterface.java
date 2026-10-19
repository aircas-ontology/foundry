package com.aircas.ptr.foundry.ontology.model.dto.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 容器算子的单个接口（从 OpenAPI/Swagger 文档探查得到，用户勾选后保存）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContainerInterface {

    /** 接口名称：operationId 或 summary，缺省用 method+path */
    private String name;

    /** 接口标识：{@code METHOD path}，如 POST /detect */
    private String api;

    /** 请求方式：GET/POST/PUT/DELETE... */
    private String method;

    /** 请求路径：/detect */
    private String path;

    /** 描述 */
    private String description;

    /** 入参（query/header/path 参数 + requestBody 属性） */
    private List<ContainerInterfaceParam> inputParameters;

    /** 出参（2xx 响应 application/json schema 属性） */
    private List<ContainerInterfaceParam> outputParameters;
}
