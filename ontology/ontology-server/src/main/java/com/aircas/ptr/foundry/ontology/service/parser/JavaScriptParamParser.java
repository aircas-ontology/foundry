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
import com.github.javaparser.ast.type.ClassOrInterfaceType;
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
                    out.add(ScriptParsedParam.builder()
                            .name(paramName)
                            .type(ScriptParamTypeMapper.map(typeText))
                            .rawType(typeText)
                            .category(FunctionParamCategoryEnum.INPUT)
                            .source("ANNOTATION")
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
                            new LinkedHashSet<>(), 1))
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
                    ScriptParsedParam.ScriptParsedParamBuilder fb = ScriptParsedParam.builder()
                            .name(var.getNameAsString())
                            .type(ScriptParamTypeMapper.map(fieldTypeText))
                            .rawType(fieldTypeText)
                            .category(category)
                            .source(source);
                    // 字段类型本身也是自定义类 → 递归展开
                    ClassOrInterfaceDeclaration nestedClass = resolveUserClass(fieldTypeText, classByName);
                    if (nestedClass != null && visited.add(fieldTypeText)) {
                        fb.fields(expandFields(nestedClass, category, source, classByName, expander, visited, depth + 1));
                    } else {
                        // 集合泛型：List<Admin> / Set<User> → 提取泛型参数递归展开
                        String elementType = extractCollectionElementType(var.getType());
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
