package com.aircas.ptr.foundry.ontology.model.enums;

/**
 * 属性元数据 schema 节点的取值类型。
 * INPUT 输入值（自由填写）；ENUM 枚举值（value 为逗号分隔的候选项，前端下拉提示，不强制校验）；
 * RANGE 范围值（value 形如 "min,max"）。
 */
public enum MetadataValueTypeEnum {
    INPUT,
    ENUM,
    RANGE
}