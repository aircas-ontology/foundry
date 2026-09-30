package com.aircas.ptr.foundry.ontology.script.security.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AST 中出现的一次「方法调用」。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AstCallRef {

    private String methodName;

    /**
     * 接收者/宿主的类名：优先取解析后的类型全限定名，未解析时退化为源码文本（如 Runtime）
     */
    private String receiverType;

    /**
     * 接收者的简单类名（如 Runtime），用于按简单名配置的规则匹配
     */
    private String receiverSimpleName;

    /**
     * 接收者本身也是一次方法调用时的方法名（如 Class.forName(...).getMethod(...) 中的 forName）。
     * 用于反射链判定：不依赖类型解析结果，即使动态派发也能还原调用链。
     */
    private String receiverMethodName;

    private int lineNumber;

    private int columnNumber;

    private String snippet;
}
