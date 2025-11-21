package com.aircas.ptr.foundry.ontology.service.impl;
import com.aircas.ptr.foundry.common.constant.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.common.constant.FunctionParamType;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.aircas.ptr.foundry.ontology.utils.SchemaHandleUtil;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import groovy.lang.GroovyClassLoader;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroovyServiceImpl implements GroovyService {


    /**
     * 解析groovy代码块，获取参数列表和返回值信息   |   旧方法
     * @param code
     * @return
     */
    /*@Override
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
                    GenericsType[] genericsTypes = type.getGenericsTypes();
                    String typeName = type.getName();
                    if(genericsTypes != null && genericsTypes.length > 0 ){
                        typeName = String.format("%s<%s>",typeName,String.join(",", Arrays.stream(genericsTypes).map(g -> g.getType().getName()).collect(Collectors.toList())));
                    }
                    String name = parameter.getName();
                    boolean basicType = isBasicType(type);
                    FunctionParamDTO paramDTO = FunctionParamDTO.builder()
                            .paramName(name)
                            .referenceName(typeName)
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
                String typeName = returnType.getName();
                GenericsType[] returnGenericsTypes = returnType.getGenericsTypes();
                if(returnGenericsTypes != null && returnGenericsTypes.length > 0 ){
                    typeName = String.format("%s<%s>",typeName,String.join(",", Arrays.stream(returnGenericsTypes).map(g -> g.getType().getName()).collect(Collectors.toList())));
                }
                FunctionParamDTO returnParam = FunctionParamDTO.builder()
                        .category(FunctionParamCategoryEnum.OUTPUT.toString())
                        //todo List/Map/Set等
                        .paramType(basicType ? FunctionParamType.getTypeEnum(returnType.getName()) : "OBJECT")
                        .referenceName(typeName)
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
    }*/

    @Override
    public List<FunctionParamDTO> parseFunctionParam(String code) {
        //Groovy编译器配置
        var config = new CompilerConfiguration();
        var compilationUnit = new CompilationUnit(config);
        //添加代码块
        compilationUnit.addSource("temp.groovy",code);
        //compile方法用于执行编译过程,Phases.SEMANTIC_ANALYSIS 表示编译到语义分析阶段。这个阶段会生成抽象语法树（AST），但不会生成字节码
        compilationUnit.compile(Phases.SEMANTIC_ANALYSIS);
        var params = new ArrayList<FunctionParamDTO>();
        //提取类信息
        for(ClassNode classNode : compilationUnit.getAST().getClasses()){
            //提取方法信息
            var paramList = classNode.getMethods().stream().filter(m -> StringUtils.equals("handle",m.getName())).map(m -> {
                List<FunctionParamDTO> funcParams = new ArrayList<>();
                //1.参数信息提取
                var parameters = m.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    var parameter = parameters[i];
                    var name = parameter.getName();
                    var param = extractParamInfo(parameter.getType(), FunctionParamCategoryEnum.INPUT, i + 1, name);
                    funcParams.add(param);
                }
                //2.返回值信息提取
                var returnParam = extractParamInfo(m.getReturnType(), FunctionParamCategoryEnum.OUTPUT, 0, "result");
                funcParams.add(returnParam);
                return funcParams;
            }).flatMap(List::stream).collect(Collectors.toList());
            params.addAll(paramList);
        }
        return params;
    }

    /**
     * 提取参数/返回值信息
     * @param type
     * @param category
     * @return
     */
    private FunctionParamDTO extractParamInfo(ClassNode type,FunctionParamCategoryEnum category,int order,String paramName){
        var basicType = isBasicType(type);
        //全限定类名
        var typeName = type.getName();
        //泛型列表
        var genericsTypes = type.getGenericsTypes();
        if(genericsTypes != null && genericsTypes.length > 0 ){
            typeName = String.format("%s<%s>",typeName,String.join(",", Arrays.stream(genericsTypes).map(g -> g.getType().getName()).collect(Collectors.toList())));
        }
        //获取复杂类型参数json结构,基本类型不设定
        var schema = basicType ? null : SchemaHandleUtil.parser(typeName);
        return FunctionParamDTO.builder()
                .category(category.toString())
                .paramType(basicType ? FunctionParamType.getTypeEnum(type.getName()) : "OBJECT")
                .referenceType(typeName)
                .paramName(paramName)
                .paramOrder(order)
                .paramSchema(basicType ? null : schema)
                .build();
    }

    @SneakyThrows
    @Override
    public String executeGroovy(String code, Map<String, Object> paramMap, List<FunctionParamPO> paramInfos) {
        //编译groovy代码块，加载类信息
        var classLoader = new GroovyClassLoader();
        var groovyClass = classLoader.parseClass(code);
        var groovyInstance = groovyClass.getDeclaredConstructor(new Class[0]).newInstance();
        //获取参数类型列表
        var classList = paramInfos.stream().map(p -> SchemaHandleUtil.getClassByTypeExpression(p.getDescription())).collect(Collectors.toList());
        //筛选handle函数方法
        var method = groovyClass.getMethod("handle", classList.toArray(new Class[]{}));
        //参数列表反序列化
        var paramValues = paramInfos.stream().map(p -> {
            var value = paramMap.get(p.getParamName());
            if(Objects.nonNull(value)){
                //非基本类型
                if(p.getParamType() == FunctionParamTypeEnum.OBJECT){
                    return SchemaHandleUtil.resolveJson2Obj(p.getDescription(),value.toString());
                //基本类型
                }else{
                    return SchemaHandleUtil.convertValue(value,p.getDescription());
                }
            }
            return null;
        }).collect(Collectors.toList());
        //动态调用handle方法
        var result = method.invoke(groovyInstance,paramValues.toArray(new Object[]{}));
        //返回值 考虑到类型多样性，暂时仅使用json返回
        return JSON.toJSONString(result.toString());
    }

    /**
     * 判断参数类型是否为基础类型【基本类型 | 字符串】
     * @param classNode
     * @return
     */
    private boolean isBasicType(ClassNode classNode){
        return ClassHelper.isPrimitiveType(classNode) || FunctionParamType.isBasicType(classNode.getName());
    }

    /**
     * 获取类结构信息 schema
     * @param clazz
     * @return
     */
    private String getParamSchema(Class clazz){
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
}
