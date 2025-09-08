package com.aircas.ptr.foundry.ontology.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OntologyVO {
    // 目录id
    Long id;
    // 目录名称
    String name;
    // 所属业务领域
    String domain;
    // 图标
    String icon;
    // 描述
    String desc;
    // 本体包含的字段
    String fileds;
    // 状态
    String status;
    // 创建时间
    String createTime;
    // 修改时间
    String modifyTime;

}
