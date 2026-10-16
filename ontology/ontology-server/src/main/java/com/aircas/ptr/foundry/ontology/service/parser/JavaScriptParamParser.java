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
 * 形参为 Map/Object 时启发式收集 {@code input.get("x")} 字面量键（INFERRED）。
 */
@Slf4j
@Component
public class JavaScriptParamParser implements ScriptParamParser {

    private static final Set<String> MAP_LIKE = Set.of("map", "hashmap", "linkedhashmap", "object", "jsonobject", "jsonnode");

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
        collectInputs(handle.get(), classByName, result);
        collectOutputs(handle.get(), classByName, result);
        return ScriptParseOutcome.ok(result);
    }

    private void collectInputs(MethodDeclaration handle, Map<String, ClassOrInterfaceDeclaration> classByName,
                               List<ScriptParsedParam> out) {
        boolean expanded = false;
        List<String> mapParamNames = new ArrayList<>();
        for (Parameter p : handle.getParameters()) {
            String typeText = p.getType().asString();
            ClassOrInterfaceDeclaration userClass = resolveUserClass(typeText, classByName);
            if (userClass != null) {
                expandFields(userClass, FunctionParamCategoryEnum.INPUT, "ANNOTATION", out);
                expanded = true;
            } else if (isMapLike(typeText)) {
                mapParamNames.add(p.getNameAsString());
                // Map 入参本身也记一个整体对象（前端可只取展开字段，此处不阻断启发）
            } else {
                out.add(ScriptParsedParam.builder()
                        .name(p.getNameAsString())
                        .type(ScriptParamTypeMapper.map(typeText))
                        .rawType(typeText)
                        .category(FunctionParamCategoryEnum.INPUT)
                        .source("ANNOTATION")
                        .build());
                expanded = true;
            }
        }
        if (!expanded && !mapParamNames.isEmpty()) {
            inferInputsFromMapGet(handle, mapParamNames, out);
        }
    }

    private void collectOutputs(MethodDeclaration handle, Map<String, ClassOrInterfaceDeclaration> classByName,
                                List<ScriptParsedParam> out) {
        String returnType = handle.getType().asString();
        ClassOrInterfaceDeclaration userClass = resolveUserClass(returnType, classByName);
        if (userClass != null) {
            expandFields(userClass, FunctionParamCategoryEnum.OUTPUT, "ANNOTATION", out);
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

    private void expandFields(ClassOrInterfaceDeclaration cls, FunctionParamCategoryEnum category, String source,
                              List<ScriptParsedParam> out) {
        Set<String> seen = new LinkedHashSet<>();
        for (FieldDeclaration field : cls.getFields()) {
            // 跳过 static 字段（如 serialVersionUID/logger 常量），它们不是实例入参/出参
            if (field.isStatic()) {
                continue;
            }
            for (VariableDeclarator var : field.getVariables()) {
                if (seen.add(var.getNameAsString())) {
                    out.add(ScriptParsedParam.builder()
                            .name(var.getNameAsString())
                            .type(ScriptParamTypeMapper.map(var.getType().asString()))
                            .rawType(var.getType().asString())
                            .category(category)
                            .source(source)
                            .build());
                }
            }
        }
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
