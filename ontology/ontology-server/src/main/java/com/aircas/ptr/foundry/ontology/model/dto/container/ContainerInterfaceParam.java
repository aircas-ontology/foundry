package com.aircas.ptr.foundry.ontology.model.dto.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 容器算子接口的单个参数（入参或出参）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContainerInterfaceParam {

    /** 参数名 */
    private String name;

    /** 参数位置：query / header / path / body（出参固定 body） */
    private String in;

    /** 是否必填 */
    private Boolean required;

    /** 数据类型：string / integer / number / boolean / object / array */
    private String type;

    /** 描述 */
    private String description;

    /**
     * 子字段（object 取 properties；array 取 items 的 properties，表示元素字段）。
     * <p>标量参数为 null。参数名保持该层级在文档中的原始字段名（不带父级前缀），
     * 执行期仍按顶层参数名组装请求体，扁平成 student.id 会让容器收到字面量键。</p>
     */
    private List<ContainerInterfaceParam> fields;
}
