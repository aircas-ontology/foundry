package com.aircas.ptr.foundry.ontology.script.security;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.script.security.model.AstCallRef;
import com.aircas.ptr.foundry.ontology.script.security.model.AstTypeRef;
import com.aircas.ptr.foundry.ontology.script.security.model.ScriptAstInventory;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 脚本 AST 解析器。
 * <p>
 * 两条必须遵守的实现约束（均已实证，见 docs/verification/AstProbe2.java）：
 * <ol>
 *   <li><b>不能用 AstBuilder</b>：其三个重载均返回 [BlockStatement, ClassNode]，拿不到 ModuleNode，
 *       而 import 挂在 ModuleNode 上。必须用 CompilationUnit + getAST().getModules()。</li>
 *   <li><b>不能用反射遍历 AST</b>：ClassNode.getArrayType() 等方法每次返回新实例，对象标识去环失效，
 *       实测直接 StackOverflowError。必须使用官方访问者 CodeVisitorSupport。</li>
 * </ol>
 * <p>
 * 关于编译期副作用（P0-3）：保存动作本身会编译脚本，而 @Grab 会在编译期触发依赖下载。
 * 因此这里始终禁用 groovy.grape.GrabAnnotationTransformation，并采用两段式编译：
 * 先在 CONVERSION 阶段读取注解并拒绝危险注解（含在 SEMANTIC_ANALYSIS 阶段执行的 @ASTTest），
 * 确认无危险注解后才进入 SEMANTIC_ANALYSIS 阶段做类型解析。
 */
@Slf4j
@Component
public class ScriptAstParser {

    private static final String SCRIPT_NAME = "ScriptSecurityScan.groovy";

    /**
     * @Grab 是全局 AST 变换，编译期会解析并下载依赖（SSRF / 依赖投毒）。扫描阶段一律禁用。
     */
    private static final String GRAB_TRANSFORMATION = "groovy.grape.GrabAnnotationTransformation";

    /**
     * 解析脚本为 ModuleNode。解析失败按 fail-closed 抛出 BusinessException。
     */
    public ModuleNode parse(String code, CompilePhase phase) {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setDisabledGlobalASTTransformations(Collections.singleton(GRAB_TRANSFORMATION));
        CompilationUnit unit = new CompilationUnit(config);
        unit.addSource(SCRIPT_NAME, code);
        try {
            unit.compile(phase.getPhaseNumber());
        } catch (MultipleCompilationErrorsException e) {
            // 语法错误：不是安全违规，但同样拒绝保存，给出明确提示
            log.warn("script security scan: parse failed, syntax error");
            throw new BusinessException("函数代码解析失败，请检查脚本语法",
                    ResultCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            // 编译器异常：fail-closed，绝不静默放行
            log.error("script security scan: parse threw unexpected exception", e);
            throw new BusinessException("函数代码安全检测失败，已拒绝保存");
        }
        List<ModuleNode> modules = unit.getAST() == null ? null : unit.getAST().getModules();
        if (modules == null || modules.isEmpty() || modules.get(0) == null) {
            throw new BusinessException("函数代码解析失败，请检查脚本内容");
        }
        return modules.get(0);
    }

    /**
     * 收集所有注解（类、方法、字段）。用于在第一阶段做危险注解检查。
     */
    public List<AstTypeRef> collectAnnotations(ModuleNode module) {
        List<AstTypeRef> result = new ArrayList<>();
        for (ClassNode cn : safe(module.getClasses())) {
            collectAnnotations(cn, result);
            for (MethodNode mn : safe(cn.getMethods())) {
                collectAnnotations(mn, result);
            }
            for (FieldNode fn : safe(cn.getFields())) {
                collectAnnotations(fn, result);
            }
        }
        return result;
    }

    /**
     * 收集完整清单（import / 构造器 / 注解 / 方法调用 / 静态方法调用）。
     */
    public ScriptAstInventory collectInventory(ModuleNode module) {
        ScriptAstInventory inventory = new ScriptAstInventory();

        for (ImportNode im : safe(module.getImports())) {
            inventory.addImport(refOf(typeNameOfImport(im), im, false));
        }
        for (ImportNode im : safe(module.getStarImports())) {
            inventory.addImport(refOf(stripTrailingDot(im.getPackageName()), im, true));
        }
        for (ImportNode im : safeMap(module.getStaticImports())) {
            inventory.addImport(refOf(typeNameOf(im.getType()), im, false));
        }
        for (ImportNode im : safeMap(module.getStaticStarImports())) {
            inventory.addImport(refOf(typeNameOf(im.getType()), im, true));
        }

        InventoryVisitor visitor = new InventoryVisitor(inventory);
        for (ClassNode cn : safe(module.getClasses())) {
            collectAnnotations(cn, visitor.getAnnotations());
            for (MethodNode mn : safe(cn.getMethods())) {
                collectAnnotations(mn, visitor.getAnnotations());
                if (mn.getCode() != null) {
                    mn.getCode().visit(visitor);
                }
            }
            for (FieldNode fn : safe(cn.getFields())) {
                collectAnnotations(fn, visitor.getAnnotations());
                if (fn.getInitialValueExpression() != null) {
                    fn.getInitialValueExpression().visit(visitor);
                }
            }
        }
        // 普通脚本会被编译成一个 Script 类，其 run() 方法体与 module.getStatementBlock() 是同一份语句。
        // 两者都遍历会导致每条调用被重复采集（违规数翻倍、复杂度虚高），故仅作为兜底。
        if (visitor.getNodeCount() == 0 && module.getStatementBlock() != null) {
            module.getStatementBlock().visit(visitor);
        }
        // 访问器收集的注解必须回写清单，否则注解规则在完整清单上永远读不到数据（静默失效）
        inventory.getAnnotations().addAll(visitor.getAnnotations());
        inventory.setNodeCount(visitor.getNodeCount());
        inventory.setMaxDepth(visitor.getMaxDepth());
        return inventory;
    }

    /**
     * 类 / 方法 / 字段都继承自 AnnotatedNode，统一在此收集，避免遗漏方法级与字段级注解。
     */
    private void collectAnnotations(AnnotatedNode node, List<AstTypeRef> target) {
        for (AnnotationNode an : safe(node.getAnnotations())) {
            target.add(refOf(typeNameOf(an.getClassNode()), an, false));
        }
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private static List<ImportNode> safeMap(Map<String, ImportNode> map) {
        return map == null ? Collections.emptyList() : new ArrayList<>(map.values());
    }

    private static String typeNameOf(ClassNode node) {
        return node == null ? null : node.getName();
    }

    private static String typeNameOfImport(ImportNode node) {
        String name = typeNameOf(node.getType());
        if (name != null && name.endsWith(".*")) {
            return name.substring(0, name.length() - 2);
        }
        return name != null ? name : stripTrailingDot(node.getPackageName());
    }

    private static String stripTrailingDot(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    private static int lineOf(org.codehaus.groovy.ast.ASTNode node) {
        return node == null ? -1 : node.getLineNumber();
    }

    private static int colOf(org.codehaus.groovy.ast.ASTNode node) {
        return node == null ? -1 : node.getColumnNumber();
    }

    private static String textOf(org.codehaus.groovy.ast.ASTNode node) {
        return node == null ? null : node.getText();
    }

    private static AstTypeRef refOf(String typeName, org.codehaus.groovy.ast.ASTNode node, boolean starImport) {
        return new AstTypeRef(typeName, lineOf(node), colOf(node), textOf(node), starImport);
    }

    /**
     * 基于官方访问者的清单收集器。禁止改为反射遍历（会栈溢出）。
     */
    private static final class InventoryVisitor extends CodeVisitorSupport {

        private final ScriptAstInventory inventory;
        private final List<AstTypeRef> annotations;
        private int depth;
        private int maxDepth;
        private int nodeCount;

        private InventoryVisitor(ScriptAstInventory inventory) {
            this.inventory = inventory;
            this.annotations = new ArrayList<>();
        }

        List<AstTypeRef> getAnnotations() {
            return annotations;
        }

        int getNodeCount() {
            return nodeCount;
        }

        int getMaxDepth() {
            return maxDepth;
        }

        private void enter() {
            nodeCount++;
            depth++;
            if (depth > maxDepth) {
                maxDepth = depth;
            }
        }

        private void exit() {
            depth--;
        }

        @Override
        public void visitMethodCallExpression(MethodCallExpression call) {
            enter();
            try {
                Expression receiver = call.getObjectExpression();
                String receiverType = resolveReceiverType(receiver);
                inventory.addMethodCall(new AstCallRef(
                        call.getMethodAsString(),
                        receiverType,
                        simpleNameOf(receiverType),
                        receiverMethodNameOf(receiver),
                        lineOf(call),
                        colOf(call),
                        textOf(call)));
                super.visitMethodCallExpression(call);
            } finally {
                exit();
            }
        }

        @Override
        public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
            enter();
            try {
                String owner = typeNameOf(call.getOwnerType());
                inventory.addStaticMethodCall(new AstCallRef(
                        call.getMethod(),
                        owner,
                        simpleNameOf(owner),
                        null,
                        lineOf(call),
                        colOf(call),
                        textOf(call)));
                super.visitStaticMethodCallExpression(call);
            } finally {
                exit();
            }
        }

        @Override
        public void visitConstructorCallExpression(ConstructorCallExpression call) {
            enter();
            try {
                inventory.addConstructorCall(refOf(typeNameOf(call.getType()), call, false));
                super.visitConstructorCallExpression(call);
            } finally {
                exit();
            }
        }

        /**
         * 接收者类型优先取解析结果；未解析（Object / 动态）时退化为源码文本，
         * 使 java.lang 默认导入下的 Runtime.getRuntime() 这类写法仍可被按名匹配。
         */
        private String resolveReceiverType(Expression receiver) {
            if (receiver == null) {
                return null;
            }
            String resolved = typeNameOf(receiver.getType());
            if (resolved != null && !"java.lang.Object".equals(resolved) && !"java.lang.Class".equals(resolved)) {
                return resolved;
            }
            if (receiver instanceof VariableExpression) {
                return ((VariableExpression) receiver).getName();
            }
            // Foo.bar() 且 Foo 未解析时是 ClassExpression，必须取其类型名，
            // 否则平台内部类（如 CommandUtil.executeCommand）会因解析失败而漏检
            if (receiver instanceof ClassExpression) {
                return typeNameOf(((ClassExpression) receiver).getType());
            }
            return resolved;
        }

        /**
         * 接收者本身是一次方法调用时，取其方法名，用于还原调用链（反射链检测依赖此字段）。
         */
        private static String receiverMethodNameOf(Expression receiver) {
            if (receiver instanceof MethodCallExpression) {
                return ((MethodCallExpression) receiver).getMethodAsString();
            }
            if (receiver instanceof StaticMethodCallExpression) {
                return ((StaticMethodCallExpression) receiver).getMethod();
            }
            return null;
        }

        private static String simpleNameOf(String typeName) {
            if (typeName == null) {
                return null;
            }
            int idx = typeName.lastIndexOf('.');
            return idx < 0 ? typeName : typeName.substring(idx + 1);
        }
    }
}
