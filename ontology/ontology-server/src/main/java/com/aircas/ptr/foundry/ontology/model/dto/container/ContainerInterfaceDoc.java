package com.aircas.ptr.foundry.ontology.model.dto.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 容器算子接口文档信息（上传接口文档接口返回并回传保存）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContainerInterfaceDoc {

    /** 原始文件名 */
    private String fileName;

    /** 服务器存储的绝对路径 */
    private String path;

    /** 文档格式：openapi3 / swagger2 */
    private String format;
}
