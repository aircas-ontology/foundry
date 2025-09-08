package com.aircas.ptr.foundry.ontology.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CatalogVO {
    Long id;                  // 目录id
    String nodeCode;          // 业务节点code
    String name;              // 目录名称
    Integer level;            // 目录层级
    String fieldCode;         // 元数据code
    String fieldEnumItemCode; // 元数据枚举
    Long parentId;            // 父目录id
    Integer seq;              // 顺序
}
