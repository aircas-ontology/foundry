package com.aircas.ptr.foundry.ontology.model.dto.container;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
