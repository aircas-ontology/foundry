package com.aircas.ptr.foundry.ontology.service.parser;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptParseOutcome;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptParsedParam;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptParseStatus;
import com.aircas.ptr.foundry.ontology.utils.ScriptParamTypeMapper;
import groovy.lang.GroovyClassLoader;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Groovy 脚本参数解析器（主应用进程内，AST 静态解析，不执行脚本）。
 * <p>
 * 解析口径：
 * <ol>
 *   <li>可靠层：handle 形参/返回值若声明为脚本内自定义类（如 {@code ExecuteOutput handle(ExecuteInput i)}），
 *       展开该类的字段作为入参/出参；</li>
 *   <li>标量层：形参为基本类型时直接落一个入参；</li>
 *   <li>启发层：handle 形参为无类型 Object（新契约 {@code def handle(input)}）时，
 *       扫描函数体收集 {@code input.xxx} 成员访问作为入参、{@code return [...]} map 字面量键作为出参。</li>
 * </ol>
 * 编译只到 CONVERSION 阶段（仅建 AST、不生成字节码、不执行），用完即关 ClassLoader 避免 Metaspace 堆积。
 */
@Slf4j
@Component
public class GroovyScriptParamParser implements ScriptParamParser {

    private static final Set<String> COLLECTION_LIKE = Set.of(
            "list", "set", "collection", "iterable", "arraylist", "linkedlist", "hashset",
            "java.util.list", "java.util.set", "java.util.collection");

    @Override
    public Set<FunctionLanguageEnum> supports() {
        return Set.of(FunctionLanguageEnum.GROOVY);
    }

    @Override
    public ScriptParseOutcome parse(FunctionLanguageEnum language, String code) {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setSourceEncoding(StandardCharsets.UTF_8.name());
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader(), config);
        try {
            CompilationUnit cu = new CompilationUnit(config, (java.security.CodeSource) null, loader);
            SourceUnit su = cu.addSource("GroovyParse_" + UUID.randomUUID() + ".groovy", code);
            cu.compile(Phases.CONVERSION);
            return extract(su.getAST());
        } catch (CompilationFailedException e) {
            log.warn("Groovy 脚本 AST 解析失败（语法错误）：{}", e.getMessage());
            return ScriptParseOutcome.of(ScriptParseStatus.SYNTAX_ERROR, e.getMessage());
        } catch (StackOverflowError e) {
            log.warn("Groovy 脚本 AST 解析递归过深：{}", e.getMessage());
            return ScriptParseOutcome.of(ScriptParseStatus.PARSE_EXCEPTION, "脚本结构过深，无法静态解析");
        } catch (Exception e) {
            log.warn("Groovy 脚本 AST 解析异常：{}", e.getMessage(), e);
            return ScriptParseOutcome.of(ScriptParseStatus.PARSE_EXCEPTION, e.getMessage());
        } finally {
            closeQuietly(loader);
        }
    }

    private ScriptParseOutcome extract(ModuleNode module) {
        List<ScriptParsedParam> result = new ArrayList<>();
        Map<String, ClassNode> classByName = new LinkedHashMap<>();
        for (ClassNode cn : module.getClasses()) {
            classByName.put(cn.getName(), cn);
            classByName.put(simpleName(cn.getName()), cn);
        }
        MethodNode handle = findHandleMethod(module, classByName);
        if (handle == null) {
            return ScriptParseOutcome.of(ScriptParseStatus.NO_HANDLE, "脚本未定义 handle 入口方法");
        }
        collectInputs(handle, classByName, result);
        collectOutputs(handle, classByName, result);
        return ScriptParseOutcome.ok(result);
    }

    private MethodNode findHandleMethod(ModuleNode module, Map<String, ClassNode> classByName) {
        // 用户类里的 handle（静态风格）优先
        for (ClassNode cn : module.getClasses()) {
            for (MethodNode m : cn.getMethods()) {
                if ("handle".equals(m.getName())) {
                    return m;
                }
            }
        }
        // 脚本顶层 def handle（module.getMethods()）
        for (MethodNode m : module.getMethods()) {
            if ("handle".equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    private void collectInputs(MethodNode handle, Map<String, ClassNode> classByName, List<ScriptParsedParam> out) {
        Parameter[] parameters = handle.getParameters();
        boolean expanded = false;
        if (parameters != null) {
            for (Parameter p : parameters) {
                ClassNode type = p.getType();
                ClassNode userClass = resolveUserClass(type, classByName);
                if (userClass != null) {
                    // 层级结构：父参数为自定义类型，字段放入 fields
                    String typeText = typeNodeText(type);
                    List<ScriptParsedParam> fields = expandFields(userClass, FunctionParamCategoryEnum.INPUT, "ANNOTATION", classByName, new LinkedHashSet<>());
                    out.add(ScriptParsedParam.builder()
                            .name(p.getName())
                            .type(ScriptParamTypeMapper.map(typeText))
                            .rawType(typeText)
                            .category(FunctionParamCategoryEnum.INPUT)
                            .source("ANNOTATION")
                            .fields(fields)
                            .build());
                    expanded = true;
                } else if (!isObjectType(type)) {
                    String typeText = typeNodeText(type);
                    out.add(ScriptParsedParam.builder()
                            .name(p.getName())
                            .type(ScriptParamTypeMapper.map(typeText))
                            .rawType(typeText)
                            .category(FunctionParamCategoryEnum.INPUT)
                            .source("ANNOTATION")
                            .build());
                    expanded = true;
                }
            }
        }
        if (!expanded) {
            // 启发层：无类型入参（def handle(input)）→ 扫 body 里 input.xxx 成员访问
            inferInputsFromBody(handle, out);
        }
    }

    private void collectOutputs(MethodNode handle, Map<String, ClassNode> classByName, List<ScriptParsedParam> out) {
        ClassNode returnType = handle.getReturnType();
        ClassNode userClass = resolveUserClass(returnType, classByName);
        if (userClass != null) {
            // 层级结构：返回类型为自定义类时，父参数带 fields
            String typeText = typeNodeText(returnType);
            List<ScriptParsedParam> fields = expandFields(userClass, FunctionParamCategoryEnum.OUTPUT, "ANNOTATION", classByName, new LinkedHashSet<>());
            out.add(ScriptParsedParam.builder()
                    .name(typeText)
                    .type(ScriptParamTypeMapper.map(typeText))
                    .rawType(typeText)
                    .category(FunctionParamCategoryEnum.OUTPUT)
                    .source("ANNOTATION")
                    .fields(fields)
                    .build());
            return;
        }
        if (!isObjectType(returnType) && !"void".equals(returnType.getName())) {
            // 返回标量：作为单个 result 输出
            String returnText = typeNodeText(returnType);
            out.add(ScriptParsedParam.builder()
                    .name("result")
                    .type(ScriptParamTypeMapper.map(returnText))
                    .rawType(returnText)
                    .category(FunctionParamCategoryEnum.OUTPUT)
                    .source("ANNOTATION")
                    .build());
            return;
        }
        // 启发层：返回 map 字面量 → 收集键
        inferOutputsFromBody(handle, out);
    }

    private List<ScriptParsedParam> expandFields(ClassNode userClass, FunctionParamCategoryEnum category, String source,
                                                    Map<String, ClassNode> classByName, Set<String> visited) {
        List<ScriptParsedParam> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        userClass.getProperties().forEach(prop -> {
            String name = prop.getName();
            if (seen.add(name)) {
                String propTypeText = typeNodeText(prop.getType());
                ScriptParsedParam.ScriptParsedParamBuilder fb = ScriptParsedParam.builder()
                        .name(name)
                        .type(ScriptParamTypeMapper.map(propTypeText))
                        .rawType(propTypeText)
                        .category(category)
                        .source(source);
                // 字段类型本身也是自定义类 → 递归展开
                ClassNode nestedClass = resolveUserClass(prop.getType(), classByName);
                if (nestedClass != null && visited.add(propTypeText)) {
                    fb.fields(expandFields(nestedClass, category, source, classByName, visited));
                } else {
                    // 集合泛型：List<Admin> → 提取泛型参数递归展开
                    String elementType = extractCollectionElementType(prop.getType());
                    if (elementType != null) {
                        ClassNode elemNode = classByName.get(elementType);
                        if (elemNode == null) {
                            elemNode = classByName.get(simpleName(elementType));
                        }
                        if (elemNode != null && visited.add(elementType)) {
                            fb.fields(expandFields(elemNode, category, source, classByName, visited));
                        }
                    }
                }
                fields.add(fb.build());
            }
        });
        return fields;
    }

    private void inferInputsFromBody(MethodNode handle, List<ScriptParsedParam> out) {
        Parameter[] parameters = handle.getParameters();
        if (parameters == null || parameters.length == 0) {
            return;
        }
        Set<String> paramNames = new LinkedHashSet<>();
        for (Parameter p : parameters) {
            paramNames.add(p.getName());
        }
        BodyAccessCollector collector = new BodyAccessCollector(paramNames);
        handle.getCode().visit(collector);
        Set<String> seen = new LinkedHashSet<>();
        for (String field : collector.getAccessed()) {
            if (seen.add(field)) {
                out.add(ScriptParsedParam.builder()
                        .name(field)
                        .type(FunctionParamTypeEnum.OBJECT)
                        .category(FunctionParamCategoryEnum.INPUT)
                        .source("INFERRED")
                        .build());
            }
        }
    }

    private void inferOutputsFromBody(MethodNode handle, List<ScriptParsedParam> out) {
        Set<String> keys = new LinkedHashSet<>();
        // 显式 return（ClassCodeVisitorSupport 会下钻 if/try/for 等嵌套语句）
        ReturnMapKeyVisitor visitor = new ReturnMapKeyVisitor();
        handle.getCode().visit(visitor);
        keys.addAll(visitor.getKeys());
        // 隐式返回：CONVERSION 阶段不会自动补 return，方法体末表达式即返回值需单独采
        Expression implicit = implicitReturnExpression(handle.getCode());
        if (implicit != null) {
            collectMapKeys(implicit, keys);
        }
        for (String key : keys) {
            out.add(ScriptParsedParam.builder()
                    .name(key)
                    .type(FunctionParamTypeEnum.OBJECT)
                    .category(FunctionParamCategoryEnum.OUTPUT)
                    .source("INFERRED")
                    .build());
        }
    }

    /** 取方法体的“末表达式”（可能是 ReturnStatement 或 ExpressionStatement），无则 null。 */
    private Expression implicitReturnExpression(Statement code) {
        Statement last = code;
        if (code instanceof BlockStatement) {
            List<Statement> statements = ((BlockStatement) code).getStatements();
            if (statements.isEmpty()) {
                return null;
            }
            last = statements.get(statements.size() - 1);
        }
        if (last instanceof ReturnStatement) {
            return ((ReturnStatement) last).getExpression();
        }
        if (last instanceof ExpressionStatement) {
            return ((ExpressionStatement) last).getExpression();
        }
        return null;
    }

    /** 收集一个 map 字面量表达式的键。 */
    private void collectMapKeys(Expression expr, Set<String> keys) {
        if (expr instanceof MapExpression) {
            for (MapEntryExpression entry : ((MapExpression) expr).getMapEntryExpressions()) {
                String literal = entry.getKeyExpression().getText();
                if (literal != null && !literal.isBlank()) {
                    keys.add(literal.replaceAll("^['\"]|['\"]$", ""));
                }
            }
        }
    }

    /** 把 ClassNode 还原成类型文本，交给统一的 ScriptParamTypeMapper。 */
    private String typeNodeText(ClassNode type) {
        if (type == null) {
            return "";
        }
        if (type.isArray()) {
            return "array";
        }
        return simpleName(type.getName());
    }

    /** 从集合类型中提取泛型元素类型名（如 List<Admin> → "Admin"），非集合返回 null。 */
    private String extractCollectionElementType(ClassNode type) {
        if (type == null) {
            return null;
        }
        // 数组类型：元素类型就是 componentType
        if (type.isArray()) {
            return typeNodeText(type.getComponentType());
        }
        String name = type.getName().toLowerCase();
        boolean isCollection = COLLECTION_LIKE.stream().anyMatch(name::endsWith);
        if (!isCollection) {
            return null;
        }
        GenericsType[] generics = type.getGenericsTypes();
        if (generics != null && generics.length > 0) {
            return typeNodeText(generics[0].getType());
        }
        return null;
    }

    private boolean isObjectType(ClassNode type) {
        if (type == null) {
            return true;
        }
        String name = type.getName();
        return "java.lang.Object".equals(name) || "Object".equals(name) || "def".equals(name);
    }

    private ClassNode resolveUserClass(ClassNode type, Map<String, ClassNode> classByName) {
        if (type == null || isObjectType(type)) {
            return null;
        }
        ClassNode exact = classByName.get(type.getName());
        if (exact != null) {
            return exact;
        }
        return classByName.get(simpleName(type.getName()));
    }

    private static String simpleName(String fullName) {
        if (fullName == null) {
            return "";
        }
        int dot = fullName.lastIndexOf('.');
        return dot >= 0 ? fullName.substring(dot + 1) : fullName;
    }

    private static void closeQuietly(GroovyClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.clearCache();
            loader.close();
        } catch (IOException ignored) {
        }
    }

    /** 扫描函数体里对入参变量的成员访问（input.xxx）。 */
    private static final class BodyAccessCollector extends ClassCodeVisitorSupport {
        /** Groovy 内置元属性，不当作业务入参。 */
        private static final Set<String> BUILTIN_PROPS =
                Set.of("class", "metaClass", "owner", "thisObject", "beanInfo");
        private final Set<String> paramNames;
        private final Set<String> accessed = new LinkedHashSet<>();

        private BodyAccessCollector(Set<String> paramNames) {
            this.paramNames = paramNames;
        }

        @Override
        public void visitPropertyExpression(PropertyExpression expression) {
            Expression object = expression.getObjectExpression();
            if (object instanceof VariableExpression
                    && paramNames.contains(((VariableExpression) object).getName())) {
                String prop = expression.getPropertyAsString();
                if (prop != null && !prop.isBlank() && !BUILTIN_PROPS.contains(prop)) {
                    accessed.add(prop);
                }
            }
            super.visitPropertyExpression(expression);
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return null;
        }

        Set<String> getAccessed() {
            return accessed;
        }
    }

    /** 扫描函数体内所有 return 语句里的 map 字面量键（含嵌套 if/try/for）。 */
    private static final class ReturnMapKeyVisitor extends ClassCodeVisitorSupport {
        private final Set<String> keys = new LinkedHashSet<>();

        @Override
        public void visitReturnStatement(ReturnStatement statement) {
            Expression expr = statement.getExpression();
            if (expr instanceof MapExpression) {
                for (MapEntryExpression entry : ((MapExpression) expr).getMapEntryExpressions()) {
                    String literal = entry.getKeyExpression().getText();
                    if (literal != null && !literal.isBlank()) {
                        keys.add(literal.replaceAll("^['\"]|['\"]$", ""));
                    }
                }
            }
            super.visitReturnStatement(statement);
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return null;
        }

        Set<String> getKeys() {
            return keys;
        }
    }
}
