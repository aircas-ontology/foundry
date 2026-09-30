package com.aircas.ptr.foundry.ontology.script.security.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次解析产出的 AST 清单。规则只读取本对象，不直接遍历 AST，保证解析与判定解耦。
 */
@Data
public class ScriptAstInventory {

    /** import / star import / static import */
    private final List<AstTypeRef> imports = new ArrayList<>();

    /** 构造器调用（new Xxx(...)） */
    private final List<AstTypeRef> constructorCalls = new ArrayList<>();

    /** 注解（类、方法、字段上的） */
    private final List<AstTypeRef> annotations = new ArrayList<>();

    /** 实例/链式方法调用（含 Groovy 扩展方法） */
    private final List<AstCallRef> methodCalls = new ArrayList<>();

    /** 静态方法调用（Class.method(...)） */
    private final List<AstCallRef> staticMethodCalls = new ArrayList<>();

    /** AST 节点总数 */
    private int nodeCount;

    /** AST 最大嵌套深度 */
    private int maxDepth;

    public void addImport(AstTypeRef ref) {
        imports.add(ref);
    }

    public void addConstructorCall(AstTypeRef ref) {
        constructorCalls.add(ref);
    }

    public void addAnnotation(AstTypeRef ref) {
        annotations.add(ref);
    }

    public void addMethodCall(AstCallRef ref) {
        methodCalls.add(ref);
    }

    public void addStaticMethodCall(AstCallRef ref) {
        staticMethodCalls.add(ref);
    }
}
