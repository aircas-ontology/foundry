package com.aircas.ptr.foundry.common.pg;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段的meta数据描述定义
 * 
 * @author yibo.tang
 * @date 2021-04-25 14:52:03
 * @since 1.0
 *
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FieldMeta {
	/** 列名 */
    private String  columnName;
    /** 类型 */
    private String  columnType;
    /** 是否可为null */
    private boolean nullable;
    /** 是否为主键 */
    private boolean primaryKey;
    /** 默认值 */
    private String  defaultValue;
}
