package com.aircas.ptr.foundry.ontology.service.parser;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptParseOutcome;
import com.aircas.ptr.foundry.ontology.model.dto.ScriptParsedParam;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ScriptParseStatus;
import com.aircas.ptr.foundry.ontology.utils.ScriptParamTypeMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Java 脚本参数解析器（主应用进程内，基于 JavaParser 纯语法 AST 解析，不编译不执行）。
 * <p>
 * 口径同 Groovy 解析器：优先展开 handle 形参/返回值的自定义类型字段（ANNOTATION），
 * 类型是脚本 {@code import} 进来的工程类（如 {@code FunctionResultVO}）时按 import 补全全限定名后反射展开下一级，
 * 形参为 Map/Object 时启发式收集 {@code input.get("x")} 字面量键（INFERRED）。
 * 工程内类展开统一委托给 {@link ProjectClassExpander}。
 */
@Slf4j
@Component
public class JavaScriptParamParser implements ScriptParamParser {

    private static final Set<String> MAP_LIKE = Set.of("map", "hashmap", "linkedhashmap", "object", "jsonobject", "jsonnode");
    private static final Set<String> COLLECTION_LIKE = Set.of("list", "set", "collection", "iterable", "arraylist", "linkedlist", "hashset");

    @Override
    public Set<FunctionLanguageEnum> supports() {
        return Set.of(FunctionLanguageEnum.JAVA);
    }

    @Override
    public ScriptParseOutcome parse(FunctionLanguageEnum language, String code) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(code);
        } catch (StackOverflowError e) {
            log.warn("Java 脚本 AST 解析递归过深：{}", e.getMessage());
            return ScriptParseOutcome.of(ScriptParseStatus.PARSE_EXCEPTION, "脚本结构过深，无法静态解析");
        } catch (Exception e) {
            log.warn("Java 脚本 AST 解析失败（语法错误）：{}", e.getMessage());
            return ScriptParseOutcome.of(ScriptParseStatus.SYNTAX_ERROR, e.getMessage());
        }
        if (cu == null) {
            return ScriptParseOutcome.of(ScriptParseStatus.SYNTAX_ERROR, "脚本无法解析");
        }
        Map<String, ClassOrInterfaceDeclaration> classByName = new LinkedHashMap<>();
        for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            classByName.put(cls.getNameAsString(), cls);
        }
        Optional<MethodDeclaration> handle = cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> "handle".equals(m.getNameAsString()))
                .findFirst();
        if (handle.isEmpty()) {
            return ScriptParseOutcome.of(ScriptParseStatus.NO_HANDLE, "脚本未定义 handle 入口方法");
        }
        List<ScriptParsedParam> result = new ArrayList<>();
        ProjectClassExpander expander = new ProjectClassExpander(code);
        collectInputs(handle.get(), classByName, expander, result);
        collectOutputs(handle.get(), classByName, expander, result);
        return ScriptParseOutcome.ok(result);
    }

    private void collectInputs(MethodDeclaration handle, Map<String, ClassOrInterfaceDeclaration> classByName,
                               ProjectClassExpander expander, List<ScriptParsedParam> out) {
        boolean expanded = false;
        List<String> mapParamNames = new ArrayList<>();
        for (Parameter p : handle.getParameters()) {
            String typeText = p.getType().asString();
            String paramName = p.getNameAsString();
            ClassOrInterfaceDeclaration userClass = resolveUserClass(typeText, classByName);
            if (userClass != null) {
                // 层级结构：父参数为自定义类型，字段放入 fields
                List<ScriptParsedParam> fields = expandFields(userClass, FunctionParamCategoryEnum.INPUT, "ANNOTATION",
                        classByName, expander, new LinkedHashSet<>(), 1);
                out.add(ScriptParsedParam.builder()
                        .name(paramName)
                        .type(ScriptParamTypeMapper.map(typeText))
                        .rawType(typeText)
                        .category(FunctionParamCategoryEnum.INPUT)
                        .source("ANNOTATION")
                        .fields(fields)
                        .build());
                expanded = true;
            } else if (isMapLike(typeText)) {
                mapParamNames.add(paramName);
            } else {
                Class<?> projectClass = expander.resolve(typeText);
                if (projectClass != null) {
                    // 工程内类（脚本 import 进来的 VO/DTO）：rawType 用真实全限定名，字段反射展开下一级
                    out.add(ScriptParsedParam.builder()
                            .name(paramName)
                            .type(ScriptParamTypeMapper.map(typeText))
                            .rawType(projectClass.getName())
                            .category(FunctionParamCategoryEnum.INPUT)
                            .source("ANNOTATION")
                            .fields(expander.expandFields(projectClass, FunctionParamCategoryEnum.INPUT,
                                    new LinkedHashSet<>(), 1))
                            .build());
                } else {
                    // 集合参数：元素类型决定下一级结构（List<OrbitPoint> 展开字段）与展示（List<Double> 标元素类型）
                    String elementType = extractCollectionElementType(p.getType());
                    List<ScriptParsedParam> elementFields = elementType == null ? null
                            : expandCollectionElementFields(p.getType(), FunctionParamCategoryEnum.INPUT, classByName, expander);
                    out.add(ScriptParsedParam.builder()
                            .name(paramName)
                            .type(ScriptParamTypeMapper.map(typeText))
                            .rawType(typeText)
                            .elementType(elementType)
                            .category(FunctionParamCategoryEnum.INPUT)
                            .source("ANNOTATION")
                            .fields(elementFields)
                            .build());
                }
                expanded = true;
            }
        }
        if (!expanded && !mapParamNames.isEmpty()) {
            inferInputsFromMapGet(handle, mapParamNames, out);
        }
    }

    private void collectOutputs(MethodDeclaration handle, Map<String, ClassOrInterfaceDeclaration> classByName,
                                ProjectClassExpander expander, List<ScriptParsedParam> out) {
        String returnType = handle.getType().asString();
        ClassOrInterfaceDeclaration userClass = resolveUserClass(returnType, classByName);
        if (userClass != null) {
            // 层级结构：返回类型为自定义类时，父参数带 fields
            List<ScriptParsedParam> fields = expandFields(userClass, FunctionParamCategoryEnum.OUTPUT, "ANNOTATION",
                    classByName, expander, new LinkedHashSet<>(), 1);
            out.add(ScriptParsedParam.builder()
                    .name(returnType)
                    .type(ScriptParamTypeMapper.map(returnType))
                    .rawType(returnType)
                    .category(FunctionParamCategoryEnum.OUTPUT)
                    .source("ANNOTATION")
                    .fields(fields)
                    .build());
            return;
        }
        Class<?> projectClass = expander.resolve(returnType);
        if (projectClass != null) {
            // 老契约：返回工程内 VO（如 FunctionResultVO），出参统一命名 result，字段反射展开下一级
            out.add(ScriptParsedParam.builder()
                    .name("result")
                    .type(ScriptParamTypeMapper.map(returnType))
                    .rawType(projectClass.getName())
                    .category(FunctionParamCategoryEnum.OUTPUT)
                    .source("ANNOTATION")
                    .fields(expander.expandFields(projectClass, FunctionParamCategoryEnum.OUTPUT,
                            new LinkedHashSet<>(), 1, typeVarBindings(projectClass, handle.getType()),
                            (className, category, visited, depth) -> expandScriptType(className, category, classByName, expander, visited, depth)))
                    .build());
            return;
        }
        // 返回集合（List<OrbitPoint>）：外层类型名查不到脚本内类，元素类型才是业务结构
        List<ScriptParsedParam> elementFields = expandCollectionElementFields(handle.getType(),
                FunctionParamCategoryEnum.OUTPUT, classByName, expander);
        if (elementFields != null) {
            out.add(ScriptParsedParam.builder()
                    .name("result")
                    .type(ScriptParamTypeMapper.map(returnType))
                    .rawType(returnType)
                    .category(FunctionParamCategoryEnum.OUTPUT)
                    .source("ANNOTATION")
                    .fields(elementFields)
                    .build());
            return;
        }
        if (!"void".equalsIgnoreCase(returnType) && !isMapLike(returnType)) {
            out.add(ScriptParsedParam.builder()
                    .name("result")
                    .type(ScriptParamTypeMapper.map(returnType))
                    .rawType(returnType)
                    .category(FunctionParamCategoryEnum.OUTPUT)
                    .source("ANNOTATION")
                    .build());
        }
        // Map/Object 返回：Java 无 map 字面量，静态不可知，留空由前端补充
    }

    /**
     * 集合类型的元素字段：返回「一个元素」的结构模板。
     * 元素本身仍是集合时（{@code List<List<Point>>} 的元素是 {@code List<Point>}）保留一层 list 节点，
     * 其 fields 为内层元素模板；元素是业务类时展开其字段（{@code List<Point>} → [latitude, longitude]）。
     */
    private List<ScriptParsedParam> expandCollectionElementFields(Type collectionType,
                                                                 FunctionParamCategoryEnum category,
                                                                 Map<String, ClassOrInterfaceDeclaration> classByName,
                                                                 ProjectClassExpander expander) {
        return collectionElementTemplate(collectionType, category, classByName, expander, 1);
    }

    /**
     * 递归构造集合的元素模板：元素是集合则保留一层 list 节点并下钻，元素是业务类则展开其字段；
     * 元素不是可展开结构（{@code List<String>}）时返回 null，由调用方回落标量层。
     */
    private List<ScriptParsedParam> collectionElementTemplate(Type collectionType,
                                                              FunctionParamCategoryEnum category,
                                                              Map<String, ClassOrInterfaceDeclaration> classByName,
                                                              ProjectClassExpander expander, int depth) {
        if (depth > ProjectClassExpander.MAX_FIELD_DEPTH) {
            return null;
        }
        Type element = collectionElementTypeNode(collectionType);
        if (element == null) {
            return null;
        }
        Type innerElement = collectionElementTypeNode(element);
        if (innerElement != null) {
            // 元素还是集合（List<List<X>>）：保留这一层 list 节点，fields 装内层元素模板
            List<ScriptParsedParam> inner = collectionElementTemplate(element, category, classByName, expander, depth + 1);
            if (inner == null) {
                return null;
            }
            return List.of(ScriptParsedParam.builder()
                    .name("element")
                    .type(FunctionParamTypeEnum.LIST)
                    .rawType(element.asString())
                    .elementType(innerElement.asString())
                    .category(category)
                    .source("ANNOTATION")
                    .fields(inner)
                    .build());
        }
        // 元素是业务类：先按脚本内类展开（AST），认不出再试工程内类（反射）
        String elementText = element.asString();
        List<ScriptParsedParam> fields = expandScriptType(elementText, category,
                classByName, expander, new LinkedHashSet<>(), depth);
        return fields != null ? fields
                : expander.expandFieldsByName(elementText, category, new LinkedHashSet<>(), depth);
    }

    /** 集合/数组的直接元素类型节点；非集合返回 null。 */
    private Type collectionElementTypeNode(Type type) {
        if (type instanceof ArrayType arrayType) {
            return arrayType.getComponentType();
        }
        if (!(type instanceof ClassOrInterfaceType classType)) {
            return null;
        }
        String baseName = baseName(classType.getName().getIdentifier());
        if (!COLLECTION_LIKE.contains(baseName.toLowerCase())) {
            return null;
        }
        return classType.getTypeArguments()
                .filter(args -> !args.isEmpty())
                .map(args -> (Type) args.get(0))
                .orElse(null);
    }

    /** 集合元素本身还是集合（如 {@code List<List<X>>}），需要保留中间 list 节点而非拍平。 */
    private boolean isMultiLevelCollection(Type type) {
        Type element = collectionElementTypeNode(type);
        return element != null && collectionElementTypeNode(element) != null;
    }

    private List<ScriptParsedParam> expandFields(ClassOrInterfaceDeclaration cls, FunctionParamCategoryEnum category, String source,
                                                    Map<String, ClassOrInterfaceDeclaration> classByName,
                                                    ProjectClassExpander expander, Set<String> visited, int depth) {
        List<ScriptParsedParam> fields = new ArrayList<>();
        if (depth > ProjectClassExpander.MAX_FIELD_DEPTH) {
            return fields;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (FieldDeclaration field : cls.getFields()) {
            // 跳过 static 字段（如 serialVersionUID/logger 常量），它们不是实例入参/出参
            if (field.isStatic()) {
                continue;
            }
            for (VariableDeclarator var : field.getVariables()) {
                if (seen.add(var.getNameAsString())) {
                    String fieldTypeText = var.getType().asString();
                    String elementType = extractCollectionElementType(var.getType());
                    ScriptParsedParam.ScriptParsedParamBuilder fb = ScriptParsedParam.builder()
                            .name(var.getNameAsString())
                            .type(ScriptParamTypeMapper.map(fieldTypeText))
                            .rawType(fieldTypeText)
                            .elementType(elementType)
                            .category(category)
                            .source(source);
                    // 字段类型本身也是自定义类 → 递归展开
                    ClassOrInterfaceDeclaration nestedClass = resolveUserClass(fieldTypeText, classByName);
                    if (nestedClass != null && visited.add(fieldTypeText)) {
                        fb.fields(expandFields(nestedClass, category, source, classByName, expander, visited, depth + 1));
                    } else if (isMultiLevelCollection(var.getType())) {
                        // 多级集合（List<List<X>>）：保留中间 list 节点，不拍平到最内层
                        fb.fields(collectionElementTemplate(var.getType(), category, classByName, expander, depth + 1));
                    } else {
                        // 集合泛型：List<Admin> / Set<User> → 提取泛型参数递归展开
                        String probeType = elementType == null ? fieldTypeText : elementType;
                        ClassOrInterfaceDeclaration elemClass = resolveUserClass(probeType, classByName);
                        if (elemClass != null && visited.add(probeType)) {
                            fb.fields(expandFields(elemClass, category, source, classByName, expander, visited, depth + 1));
                        } else {
                            // 脚本内类没命中 → 试工程内类（import 进来的 VO/DTO），反射展开下一级
                            fb.fields(expander.expandFieldsByName(probeType, category, visited, depth + 1));
                        }
                    }
                    fields.add(fb.build());
                }
            }
        }
        return fields;
    }

    /**
     * 泛型形参名到脚本写的泛型实参文本的绑定（如 {@code T → List<OrbitInfo>}）。
     * <p>反射只认得 {@code FunctionResultVO<T>} 里的类型变量，实参只写在脚本签名上；按声明顺序配对，
     * 通配符与 Object 视为未绑定，展开口径与改造前一致。</p>
     */
    private Map<String, String> typeVarBindings(Class<?> projectClass, Type returnType) {
        List<Type> arguments = new ArrayList<>();
        if (returnType instanceof ClassOrInterfaceType genericType) {
            genericType.getTypeArguments().ifPresent(arguments::addAll);
        }
        var variables = projectClass.getTypeParameters();
        if (arguments.isEmpty() || variables.length == 0) {
            return Map.of();
        }
        Map<String, String> bindings = new LinkedHashMap<>();
        for (int i = 0; i < variables.length && i < arguments.size(); i++) {
            Type argument = arguments.get(i);
            String argumentText = argument.asString();
            if (argument instanceof WildcardType || "Object".equals(baseName(argumentText))) {
                continue;
            }
            bindings.put(variables[i].getName(), argumentText);
        }
        return bindings;
    }

    /**
     * 泛型实参指向脚本内类（如 {@code class OrbitInfo}）时的字段展开：这类只存在于脚本源码里、
     * 不在 classpath 上，反射拿不到，只能按 AST 展开。不是脚本内类时返回 null，由调用方回落工程内类。
     */
    private List<ScriptParsedParam> expandScriptType(String className, FunctionParamCategoryEnum category,
                                                     Map<String, ClassOrInterfaceDeclaration> classByName,
                                                     ProjectClassExpander expander, Set<String> visited, int depth) {
        ClassOrInterfaceDeclaration cls = resolveUserClass(className, classByName);
        if (cls == null || !visited.add(cls.getNameAsString())) {
            return null;
        }
        return expandFields(cls, category, "ANNOTATION", classByName, expander, visited, depth);
    }

    /** 启发层：扫描 handle 体内 {@code input.get("x")} 字面量键，作为入参。 */
    private void inferInputsFromMapGet(MethodDeclaration handle, List<String> mapParamNames, List<ScriptParsedParam> out) {
        Set<String> seen = new LinkedHashSet<>();
        handle.findAll(MethodCallExpr.class).forEach(call -> {
            if ("get".equals(call.getNameAsString()) && call.getScope().isPresent()) {
                String scope = call.getScope().get().toString();
                if (mapParamNames.contains(scope) && call.getArguments().size() == 1) {
                    Expression arg = call.getArgument(0);
                    if (arg instanceof StringLiteralExpr) {
                        String key = ((StringLiteralExpr) arg).getValue();
                        if (seen.add(key)) {
                            out.add(ScriptParsedParam.builder()
                                    .name(key)
                                    .type(FunctionParamTypeEnum.OBJECT)
                                    .category(FunctionParamCategoryEnum.INPUT)
                                    .source("INFERRED")
                                    .build());
                        }
                    }
                }
            }
        });
        // 未命中字面量 get 时，退而收集对入参变量的任意属性/方法引用名（保守，仅提示用）
    }

    private ClassOrInterfaceDeclaration resolveUserClass(String typeText,
                                                          Map<String, ClassOrInterfaceDeclaration> classByName) {
        String base = baseName(typeText);
        if (base.isEmpty() || isMapLike(typeText) || ScriptParamTypeMapper.isScalarType(typeText)) {
            return null;
        }
        // 首字母大写且非 JDK 类型才可能是用户类
        return classByName.get(base);
    }

    /** 从集合类型中提取泛型元素类型名（如 List<Admin> → "Admin"），非集合返回 null。 */
    private String extractCollectionElementType(com.github.javaparser.ast.type.Type type) {
        // 数组写法（OrbitPoint[]）：组件类型就是元素类型，与 Groovy 解析器 isArray() 的口径一致
        if (type instanceof ArrayType arrayType) {
            return arrayType.getComponentType().asString();
        }
        if (!(type instanceof ClassOrInterfaceType classType)) {
            return null;
        }
        String baseName = baseName(classType.getName().getIdentifier());
        if (!COLLECTION_LIKE.contains(baseName.toLowerCase())) {
            return null;
        }
        return classType.getTypeArguments()
                .filter(args -> !args.isEmpty())
                .map(args -> args.get(0).asString())
                .orElse(null);
    }

    private boolean isMapLike(String typeText) {
        return MAP_LIKE.contains(baseName(typeText).toLowerCase());
    }

    private String baseName(String typeText) {
        if (typeText == null) {
            return "";
        }
        String s = typeText.trim();
        int angle = s.indexOf('<');
        if (angle > 0) {
            s = s.substring(0, angle);
        }
        s = s.replace("[]", "").trim();
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

}
