package com.aircas.ptr.foundry.ontology.application.service.impl;


import com.aircas.ptr.foundry.common.util.FileUtil;
import com.aircas.ptr.foundry.common.util.StringUtil;
import com.aircas.ptr.foundry.model.po.Function;
import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.Exception.*;
import com.aircas.ptr.foundry.ontology.GroovyClassLoaderManager;
import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.FunctionVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.entity.vo.ParameterMetadataVO;
import com.aircas.ptr.foundry.ontology.function.FunctionUtils;
import com.aircas.ptr.foundry.ontology.function.Parameter;
import com.aircas.ptr.foundry.ontology.repository.dao.FunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import lombok.RequiredArgsConstructor;
import com.aircas.ptr.foundry.model.po.OntologyDataType;
import com.aircas.ptr.foundry.ontology.Exception.ExceptionFactory;

import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor

public class FunctionServiceImpl implements FunctionService {

    static String baseDir = "functions";

    @Resource
    FunctionMapper functionMapper;

    @Resource
    OntologyMetaMapper ontologyMetaMapper;

    @Override
    public int saveFunctionMetadata(FunctionBo functionBo) {
//        int count = functionMapper.selectByApi(function.getApi());
//        if (count != 0) {
//            throw new DuplicatedDataException("函数已经存在");
//        }

        Function function = new Function();
        BeanUtils.copyProperties(functionBo, function);
        function.setStatus(1);
        function.setCreateTime(new Date());
        function.setUpdateTime(new Date());
        int count = functionMapper.insert(function);
        return count;
    }

    @Override
    public int updateFunctionMetadata(FunctionBo functionBo) {
//        int count = functionMapper.selectByApi(function.getApi());
//        if (count != 0) {
//            throw new DuplicatedDataException("函数已经存在");
//        }

        Function function = new Function();
        BeanUtils.copyProperties(functionBo, function);
        function.setStatus(1);
        function.setUpdateTime(new Date());
        int count = functionMapper.updateByApi(function);
        return count;
    }

    @Override
    public FunctionVO getFunctionByApi(String api) {
        Function function = functionMapper.selectByApi(api);
        FunctionVO functionVO = new FunctionVO();
        BeanUtils.copyProperties(function, functionVO);
        List<FunctionVO> list = new ArrayList<FunctionVO>();
        list.add(functionVO);
        this.setOntologyList(list);
        return functionVO;
    }

    @Override
    public Boolean delete(String functionName) {
        Integer status = functionMapper.deleteByApi(functionName);
        System.out.println(status);
        if (status == 0) {
            return false;
        }
        try {
            File file = getFile(functionName, false);
            file.delete();
        } catch (SecurityException exception) {
            System.out.println("删除文件失败");
            return false;
        }
        return true;
    }

    @Override
    public List<FunctionVO> functionMetadataList() {
        List<Function> list = functionMapper.getAllFunctions();
        List<FunctionVO> retResult = new ArrayList();
        for (Function function: list) {
            FunctionVO functionVO = new FunctionVO();
            BeanUtils.copyProperties(function, functionVO);
            retResult.add(functionVO);
        }
        this.setOntologyList(retResult);
        return retResult;
    }

    private void setOntologyList(List<FunctionVO> functionVOList) {

        Map<String, OntologyMeta> ontologyMetas = ontologyMetaMapper
                .selectAllOntologies()
                .stream()
                .map((meta) -> new Map.Entry<String, OntologyMeta>() {
                    @Override
                    public String getKey() {
                        return meta.getApiName();
                    }

                    @Override
                    public OntologyMeta getValue() {
                        return meta;
                    }

                    @Override
                    public OntologyMeta setValue(OntologyMeta value) {
                        return meta;
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        functionVOList.forEach(functionVO -> {
            String objectTypes = functionVO.getObjectTypes();
            if (objectTypes == null || objectTypes.length() == 0) {
                return;
            }
            String[] ontologyApis = objectTypes.split(",");
            List<OntologyMetaVO> list =  Arrays.stream(ontologyApis)
                    .map(api -> ontologyMetas.get(api))
                    .map(meta -> {
                        OntologyMetaVO vo = new OntologyMetaVO();
                        BeanUtils.copyProperties(meta, vo);
                        return vo;
                    })
                    .collect(Collectors.toList());
            functionVO.setOntologyList(list);
        });
    }

    @Override
    public Object handle(String functionName, Boolean isPreview, String objectTypes, HashMap<String, Object> parameters)
            throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException {

            GroovyClassLoader classLoader = GroovyClassLoaderManager.getIndependentClassLoader();
            List<String> objectApiList = this.getObjectApiList(isPreview, objectTypes, functionName);
            //将本体涉及的类都import
            importAllObjectType(classLoader, objectApiList);

            GroovyObject functionInstance = getFunctionInstance(classLoader, functionName, isPreview);

            HashMap functionProxyParameters = new HashMap();
            Method handleMethod = FunctionUtils.getMethod(functionInstance, "handle");
            functionProxyParameters.put("parameters", parameters);
            functionProxyParameters.put("parameterNames",
                    FunctionUtils.getMethodParameterAnnotates(handleMethod)
                            .stream()
                            .map((annotate) -> annotate.name())
                            .collect(Collectors.toList())
            );
            functionProxyParameters.put("instance", functionInstance);
            functionProxyParameters.put("method", handleMethod);
            GroovyObject functionProxy = FunctionUtils.getFunctionProxyInstance();
            if (functionProxy == null) {
                System.out.println("functionProxy没有初始化成功");
                return null;
            }
            try {
                return functionProxy.invokeMethod("invoke", functionProxyParameters);
            } catch (Exception exception) {
                throw ExceptionFactory.getFunctionRuntimeException(exception);
            }
    }

    @Override
    public List<ParameterMetadataVO> getParameters(String functionName, Boolean isPreview, String objectTypes)
            throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException {
        GroovyClassLoader classLoader = GroovyClassLoaderManager.getIndependentClassLoader();
        List<String> objectApiList = this.getObjectApiList(isPreview, objectTypes, functionName);
        //将本体涉及的类都import
        importAllObjectType(classLoader, objectApiList);

        GroovyObject functionInstance = getFunctionInstance(classLoader, functionName, isPreview);

        Method handleMethod = FunctionUtils.getMethod(functionInstance, "handle");
        List<ParameterMetadataVO> params = new ArrayList<>();
        List<Parameter> parameters = FunctionUtils.getMethodParameterAnnotates(handleMethod);
        List<OntologyDataType> types = FunctionUtils.getParameterTypes(handleMethod);
        for (int i = 0; i < types.size(); i ++) {
            OntologyDataType type = types.get(i);
            Parameter parameter = parameters.get(i);
            if (type.isOntologyDataType()) {
                ParameterMetadataVO vo = new ParameterMetadataVO(parameter.name(), type.name(), parameter.description(),  type.getOntologyApi());
                params.add(vo);
            } else {
                ParameterMetadataVO vo = new ParameterMetadataVO(parameter.name(), type.name(), parameter.description());
                params.add(vo);
            }
        }
        return params;
    }

    //如果api在production，则objectType从数据库读取，否则从参数读取。
    private List<String> getObjectApiList(Boolean isPreview, String objectTypes, String functionName) {
        List<String> objectApiList = new ArrayList<>();
        if (!isPreview) {
            Function function = functionMapper.selectByApi(functionName);
            objectTypes = function.getObjectTypes();
        }
        if (objectTypes != null && objectTypes.length() != 0) {
            objectApiList = Arrays.asList(objectTypes.split(","));
        }
        return objectApiList;
    }

    static private GroovyObject getFunctionInstance(GroovyClassLoader classLoader, String functionName, boolean isPreview)
            throws FunctionFileNotCompiled, FunctionClassNotNewInstanceException, FunctionNotFoundException {
        File file = getFile(functionName, isPreview);
        if (!file.exists()) {
            throw ExceptionFactory.getFunctionNotFoundException(null);
        }
        Class groovyClass;
        try {
            groovyClass = classLoader.parseClass(file);
        } catch (IOException e) {
            throw ExceptionFactory.getFunctionFileNotCompiledException(e);
        } catch (CompilationFailedException e) {
            throw ExceptionFactory.getFunctionFileNotCompiledException(e);
        }
        try {
            return (GroovyObject)groovyClass.newInstance();
        }  catch (IllegalAccessException e) {
            throw ExceptionFactory.getFunctionClassNotNewInstanceException(e);
        } catch (InstantiationException e) {
            throw ExceptionFactory.getFunctionClassNotNewInstanceException(e);
        }
    }

    private void importAllObjectType(GroovyClassLoader loader, List<String> objectApis) {
        for(String objectApi: objectApis) {
            String  className = StringUtil.capitalize(objectApi);
            String classImplString = "" +
                    "package com.aircas.ptr.foundry.ontology;" +
                    "import com.aircas.ptr.foundry.ontology.function.OntologBaseObject; " +
                    "class " + className + "  extends OntologBaseObject {  " +
                    className + "(String primaryKey) { super(\"" + objectApi + "\", primaryKey)}" +
                    "}";
            System.out.println(classImplString);
            loader.parseClass(classImplString);
        }
    }

    @Override
    public Boolean write(String functionName, String code, Boolean isPreview) {
        File file = getFile(functionName, isPreview);
        file.delete();
        try {
            if (file.createNewFile()) {
                System.out.println("file created in path" +  file.getAbsolutePath());
                String decodeCode = URLDecoder.decode(code, "UTF-8");
                FileUtils.writeStringToFile(file, decodeCode);
            } else {
                System.out.println("file already exists in path {}" + file.getAbsolutePath());
                return false;
            }
        } catch (IOException e) {
            System.out.println("create file failed");
            return false;
        }
        return true;
    }

    @Override
    public String get(String functionName, Boolean isPreview) {
        File file = getFile(functionName, isPreview);
        try {
            String code = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            String encode = URLEncoder.encode(code, "UTF-8").replace("+", "%20");
            System.out.println(code);
            return encode;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }



    private static File getFile(String functionName, Boolean isPreview) {
        createFunctionFoldersIfNeeded();
        String fileName = functionName + ".groovy";
        File path;
        if (isPreview) {
            path = FileUtils.getFile(baseDir, "preview", fileName);
        } else {
            path = FileUtils.getFile(baseDir, fileName);
        }
        return path;
    }

    private static void createFunctionFoldersIfNeeded() {
        if (FileUtil.allFiles(baseDir) == null) {
            FileUtil.createDir(baseDir);
            FileUtil.createDir(FileUtils.getFile(baseDir, "preview").getPath());
        }
    }
}
