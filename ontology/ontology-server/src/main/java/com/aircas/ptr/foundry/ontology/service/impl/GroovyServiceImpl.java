package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.aspect.FuncParam;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.aircas.ptr.foundry.ontology.utils.SchemaHandleUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroovyServiceImpl implements GroovyService {


    private final ObjectMapper objectMapper = new ObjectMapper();


    private final static String FUNCTION_RESULT_REFERENCE_NAME = "com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO";

    /**
     * 解析groovy代码块，获取参数列表和返回值信息   |   旧方法
     *
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
    public List<FunctionParamDTO> parseGroovyCode(String code) {
        //code 解析校验
        PreconditionUtils.checkArgument(StringUtils.isNotEmpty(code), "code不能为空");
        GroovyClassLoader loader = new GroovyClassLoader();
        loader.parseClass(code);
        Class[] allClasses = loader.getLoadedClasses();
        PreconditionUtils.checkArgument(allClasses != null && allClasses.length > 0, "class不能为空");
        //filter出包含handle的method且方法返回类型为FunctionResultVO
        var handleClass = Arrays.stream(allClasses).filter(clz -> {
            var handleMethod = Arrays.stream(clz.getDeclaredMethods()).filter(m -> m.getName().equals("handle")).findFirst();
            if (!handleMethod.isPresent()) {
                return false;
            }
            return StringUtils.equals(handleMethod.get().getReturnType().getName(), FUNCTION_RESULT_REFERENCE_NAME);
        }).findFirst();
        PreconditionUtils.checkArgument(handleClass.isPresent(), "函数名称handle不存在或者返回类型错误");
        //解析输入参数和输出类型
        var handleMethod = Arrays.stream(handleClass.get().getDeclaredMethods()).filter(m -> m.getName().equals("handle")).findFirst();
        JsonSchemaGenerator generator = new JsonSchemaGenerator(objectMapper);
        Parameter[] parameters = handleMethod.get().getParameters();
        List<FunctionParamDTO> funcParams = new ArrayList<>();

        try {
            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++) {
                    String paramName = getParameterName(parameters[i]);
                    Class<?> paramType = parameters[i].getType();
                    //校验参数类型，先支持基本参数类型
                    PreconditionUtils.checkArgument(FunctionParamTypeEnum.isBasicType(paramType.getName()), "暂不支持复杂类型参数", HttpStatus.BAD_REQUEST);
                    JsonSchema schema = generator.generateSchema(paramType);
                    String jsonSchema = objectMapper.writeValueAsString(schema);
                    funcParams.add(FunctionParamDTO.builder()
                            .paramName(paramName)
                            .paramType(FunctionParamTypeEnum.getByTypeName(paramType.getName()))
                            .category(FunctionParamCategoryEnum.INPUT)
                            .paramOrder(i + 1)
                            .referenceType(paramType.getName())
                            .paramSchema(jsonSchema)
                            .build());
                }
            }
            JavaType returnType = objectMapper.getTypeFactory().constructType(handleMethod.get().getGenericReturnType());
            JsonSchema returnSchema = generator.generateSchema(returnType);
            String json = objectMapper.writeValueAsString(returnSchema);
            funcParams.add(FunctionParamDTO.builder()
                    .paramName("result")
                    .paramType(FunctionParamTypeEnum.OBJECT)
                    .category(FunctionParamCategoryEnum.OUTPUT)
                    .paramOrder(1)
                    .referenceType(returnType.getRawClass().getTypeName())
                    .paramSchema(json)
                    .build());
            return funcParams;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("parseGroovyCode failed:", e);
            throw new BusinessException("parseGroovyCode failed");
        } finally {
            try {
                //关闭loader
                loader.close();
            } catch (IOException e) {
                throw new BusinessException("GroovyClassLoader close failed!");
            }
        }
    }


    @SneakyThrows
    @Override
    public String executeGroovy(String code, Map<String, Object> paramMap, List<FunctionParamPO> paramInfos) {
        //编译groovy代码块，加载类信息
        var classLoader = new GroovyClassLoader();
        classLoader.parseClass(code);
        var classes = classLoader.getLoadedClasses();
        //筛选handle函数方法
        var handleClass = Arrays.stream(classes).filter(c -> {
            var method = Arrays.stream(c.getMethods()).filter(m -> m.getName().equals("handle")).findFirst();
            return method.isPresent();
        }).findFirst();
        PreconditionUtils.checkArgument(handleClass.isPresent(), "函数handle方法不存在");
        var groovyClass = handleClass.get();
        var groovyInstance = (GroovyObject) groovyClass.newInstance();

        //参数列表反序列化
        var paramValues = paramInfos.stream().map(p -> {
            var value = paramMap.get(p.getParamName());
            //PreconditionUtils.checkArgument(value != null, "未找到函数参数：" + p.getParamName());
            //非基本类型暂时不支持
            if (p.getParamType() == FunctionParamTypeEnum.OBJECT) {
                throw new BusinessException("parse error");
            }
            //基本类型
            else {
                return SchemaHandleUtil.convertValue(value, p.getTypeReferenceName());
            }
        }).collect(Collectors.toList());
        //动态调用handle方法
        var result = CollectionUtils.isEmpty(paramValues) ?
                groovyInstance.invokeMethod("handle", null) :
                groovyInstance.invokeMethod("handle", paramValues.toArray(new Object[]{}));
        //返回值 考虑到类型多样性，暂时仅使用json返回
        var resultJsonString = objectMapper.writeValueAsString(result);
        return resultJsonString;
    }


    private String getParameterName(Parameter parameter) {
        FuncParam funcParam = parameter.getAnnotation(FuncParam.class);
        if (funcParam != null) {
            return funcParam.name();
        }
        return parameter.getName();
    }


    /**
     * 提取参数/返回值信息
     *
     * @param type
     * @param category
     * @return
     */
    private FunctionParamDTO extractParamInfo(ClassNode type, FunctionParamCategoryEnum category, Integer
            order, String paramName) {
        var basicType = isBasicType(type);
        //全限定类名
        var typeName = type.getName();
        //泛型列表
        var genericsTypes = type.getGenericsTypes();
        if (genericsTypes != null && genericsTypes.length > 0) {
            typeName = String.format("%s<%s>", typeName, String.join(",", Arrays.stream(genericsTypes).map(g -> g.getType().getName()).collect(Collectors.toList())));
        }
        //获取复杂类型参数json结构,基本类型不设定
        var schema = basicType ? null : SchemaHandleUtil.parser(typeName);
        return FunctionParamDTO.builder()
                .category(category)
                .paramType(basicType ? FunctionParamTypeEnum.getByTypeName(type.getName()) : FunctionParamTypeEnum.OBJECT)
                .referenceType(typeName)
                .paramName(paramName)
                .paramOrder(order)
                .paramSchema(basicType ? null : schema)
                .build();
    }

    /**
     * 判断参数类型是否为基础类型【基本类型 | 字符串】
     *
     * @param classNode
     * @return
     */
    private boolean isBasicType(ClassNode classNode) {
        return ClassHelper.isPrimitiveType(classNode) || FunctionParamTypeEnum.isBasicType(classNode.getName());
    }

    /**
     * 获取classNode json schema结构信息，支持泛型
     */
    private String getJsonSchemaByClassNode(ClassNode classNode) {
        try {
            Class clazz = Class.forName(classNode.getName());
            JsonSchemaGenerator generator = new JsonSchemaGenerator(objectMapper);
            JsonSchema schema = generator.generateSchema(clazz);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (Exception e) {
            log.error("getParamSchema failed", e);
            throw new BusinessException("getParamSchema failed");
        }
    }

}
