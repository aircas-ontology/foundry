package com.aircas.ptr.foundry.ontology.service.impl;
import com.aircas.ptr.foundry.common.constant.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.common.constant.FunctionParamType;
import com.aircas.ptr.foundry.common.util.StringUtil;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.service.GroovyParseService;
import com.alibaba.druid.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroovyParseServiceImpl implements GroovyParseService {


    /**
     * 解析groovy代码块，获取参数列表和返回值信息
     * @param code
     * @return
     */
    @Override
    public List<FunctionParamDTO> parseFunctionParam(String code) {
        //Groovy编译器配置
        CompilerConfiguration config = new CompilerConfiguration();
        CompilationUnit compilationUnit = new CompilationUnit(config);
        //添加代码块
        compilationUnit.addSource("temp.groovy",code);
        //compile方法用于执行编译过程,Phases.SEMANTIC_ANALYSIS 表示编译到语义分析阶段。这个阶段会生成抽象语法树（AST），但不会生成字节码
        compilationUnit.compile(Phases.SEMANTIC_ANALYSIS);
        List<FunctionParamDTO> params = new ArrayList<>();
        //提取类信息
        for(ClassNode classNode : compilationUnit.getAST().getClasses()){
            //提取方法信息
            List<FunctionParamDTO> paramList = classNode.getMethods().stream().map(m -> {
                List<FunctionParamDTO> funcParams = new ArrayList<>();
                //参数列表
                Parameter[] parameters = m.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];
                    ClassNode type = parameter.getType();
                    String name = parameter.getName();
                    boolean basicType = isBasicType(type);
                    FunctionParamDTO paramDTO = FunctionParamDTO.builder()
                            .paramName(name)
                            .category(FunctionParamCategoryEnum.INPUT.toString())
                            //todo List/Map/Set等
                            .paramType(basicType ? FunctionParamType.getTypeEnum(type.getName()) : "OBJECT")
                            .paramOrder(i + 1)
                            .paramSchema(basicType ? null : getParamSchema(type.getTypeClass()))
                            .build();
                    funcParams.add(paramDTO);
                }
                //返回值
                ClassNode returnType = m.getReturnType();
                boolean basicType = isBasicType(returnType);
                FunctionParamDTO returnParam = FunctionParamDTO.builder()
                        .category(FunctionParamCategoryEnum.OUTPUT.toString())
                        //todo List/Map/Set等
                        .paramType(basicType ? FunctionParamType.getTypeEnum(returnType.getName()) : "OBJECT")
                        .paramName("result")
                        .paramOrder(0)
                        .paramSchema(basicType ? null : getParamSchema(returnType.getTypeClass()))
                        .build();
                funcParams.add(returnParam);
                return funcParams;
            }).flatMap(List::stream).collect(Collectors.toList());
            params.addAll(paramList);
        }
        return params;
    }

    /**
     * 判断参数类型是否为基础类型【基本类型 | 字符串 | 集合 | MAP】
     * @param classNode
     * @return
     */
    public static boolean isBasicType(ClassNode classNode){
        return ClassHelper.isPrimitiveType(classNode)
                || FunctionParamType.isBasicType(classNode.getName());
    }

    /**
     * 获取类结构信息 schema
     * @param clazz
     * @return
     */
    public static String getParamSchema(Class clazz){
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonSchemaGenerator generator = new JsonSchemaGenerator(mapper);
            JsonSchema schema = generator.generateSchema(clazz);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 格式化参数类型
     * @param classNode
     * @return
     */
    public static String formatTypeName(ClassNode classNode){
        if(classNode.isUsingGenerics()){
            return String.format("%s<%s>",classNode.getName(),classNode.getGenericsTypes()[0].getType());
        }
        return classNode.getName().replace("java.lang","");
    }
}
