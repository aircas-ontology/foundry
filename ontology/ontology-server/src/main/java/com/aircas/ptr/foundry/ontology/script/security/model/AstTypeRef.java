package com.aircas.ptr.foundry.ontology.script.security.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AST 中出现的一次「类型引用」（import、构造器、静态调用宿主、声明类型等）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AstTypeRef {

    /**
     * 类型全限定名；star import 时为包前缀（已去掉尾点，如 java.net）
     */
    private String typeName;

    private int lineNumber;

    private int columnNumber;

    /**
     * 源码片段（已截断），仅用于提示，不含完整脚本
     */
    private String snippet;

    /**
     * 是否是 star import（形如 java.net.*）
     */
    private boolean starImport;
}
