package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.common.constant.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.common.constant.FunctionTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.exception.DuplicatedDataException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.common.util.StringUtil;
import com.aircas.ptr.foundry.ontology.GroovyClassLoaderManager;
import com.aircas.ptr.foundry.ontology.exception.*;
import com.aircas.ptr.foundry.ontology.function.FunctionUtils;
import com.aircas.ptr.foundry.ontology.model.bo.FunctionBo;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.Parameter;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.view.FunctionView;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionServiceImpl extends ServiceImpl<FunctionMapper, Function> implements FunctionService {

    private final static String ROOT_PATH = "functions";

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private OntologyActionMapper ontologyActionMapper;

    @Resource
    private FunctionParamService functionParamService;

    @Resource
    private GroovyService groovyService;


    @Override
    public List<FunctionView> queryFunctionViewByOntologyId(String ontologyUniqId) {
        return functionMapper.selectFunctionViewsByOntologyId(ontologyUniqId);
    }

    @Override
    public int saveFunctionMetadata(FunctionBo functionBo) {

        Function selectByApi = functionMapper.selectByApi(functionBo.getApi());
        if (selectByApi != null) {
            throw new DuplicatedDataException("函数已经存在");
        }

        Function function = new Function();
        BeanUtils.copyProperties(functionBo, function);
        Date now = new Date();
        function.setStatus(1);
        function.setCreateTime(now);
        function.setUpdateTime(now);
        function.setId(SnowflakeIdUtil.get());
        return functionMapper.insert(function);
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
    public FunctionDetailVO getFunctionDetailByApi(String api) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, api));
        PreconditionUtils.checkArgument(function != null, "函数api不存在：" + api, HttpStatus.BAD_REQUEST);
        var functionParams = functionParamService.list(
                new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
        var params = functionParams.stream().map(p ->
                        FunctionParameterVO.builder()
                                .paramId(p.getId())
                                .paramName(p.getParamName())
                                .category(p.getCategory())
                                .paramOrder(p.getParamOrder())
                                .paramSchema(p.getParamSchema())
                                .paramType(p.getParamType())
                                .description(p.getDescription())
                                .build())
                .collect(Collectors.toList());
        return FunctionDetailVO.builder()
                .code(function.getCode())
                .referenceName(function.getReferenceName())
                .params(params)
                .functionApi(function.getApi())
                .displayName(function.getDisplayName())
                .description(function.getDescription())
                .type(function.getType())
                .build();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteByApi(String api) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, api));
        PreconditionUtils.checkArgument(function != null, "无效的函数api:" + api, HttpStatus.BAD_REQUEST);
        //判断函数是否关联了行为，被本体行为使用到则不删除
        var ontologyActions = ontologyActionMapper.selectList(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getFunctionApi, api));
        PreconditionUtils.checkArgument(CollectionUtils.isEmpty(ontologyActions), "该函数已被行为关联" + api);
        //删除函数参数
        functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
        //删除函数记录
        removeById(function.getId());
        //自定义函数：删除groovy文件
        if (function.getType().equals(FunctionTypeEnum.CUSTOMIZE)) {
            deleteFunctionGroovy(api);
        }
    }


    @Override
    public FunctionVO queryById(Long id) {

        Function function = functionMapper.selectById(id);
        FunctionVO functionVO = new FunctionVO();
        BeanUtils.copyProperties(function, functionVO);
        return functionVO;
    }

    @Override
    public Boolean deleteById(Long id) {

        return functionMapper.deleteById(id) > 0 ? true : false;
    }

    @Override
    public int getCountByStatus(int status) {
        return functionMapper.selectCount(new QueryWrapper<Function>().eq("status", status));
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void createFunction(FunctionCreateParam param) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        PreconditionUtils.checkArgument(function == null, "函数api已存在:" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        //函数插入
        var func = Function.builder()
                .api(param.getFunctionApi())
                .description(param.getDescription())
                .displayName(param.getDisplayName())
                .code(param.getCode())
                .type(param.getType())
                .status(Status.ENABLE.getValue())
                .referenceName(param.getReferenceName())
                .build();
        save(func);
        //自定义函数需要解析函数参数
        if (param.getType().equals(FunctionTypeEnum.CUSTOMIZE)) {
            //解析函数参数，批量入库
            insertBatchFuncParams(func.getId(), param.getCode());
            //生成groovy文件
            writeCodeToFile(param.getFunctionApi(), param.getCode());
        }
        //todo 暂不考虑注册的外部函数
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateFunction(FunctionUpdateParam param) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        //delete function
        deleteByApi(param.getFunctionApi());
        //create function
        createFunction(FunctionCreateParam.builder()
                .functionApi(param.getFunctionApi())
                .code(param.getCode())
                .description(param.getDescription())
                .displayName(param.getDisplayName())
                .referenceName(param.getReferenceName())
                .type(function.getType())
                .build());
    }

    @Override
    public List<FunctionVO> functionMetadataList() {
        List<Function> list = functionMapper.getAllFunctions();
        List<FunctionVO> retResult = new ArrayList();
        for (Function function : list) {
            FunctionVO functionVO = new FunctionVO();
            BeanUtils.copyProperties(function, functionVO);
            retResult.add(functionVO);
        }
        this.setOntologyList(retResult);
        return retResult;
    }

    @Override
    public Page<FunctionInfoVO> getFunctions(Integer pageNum, Integer pageSize) {
        var functionPage = page(new Page<>(pageNum, pageSize));
        var records = functionPage.getRecords().stream().<FunctionInfoVO>map(func ->
                FunctionInfoVO.builder()
                        .functionApi(func.getApi())
                        .displayName(func.getDisplayName())
                        .type(func.getType())
                        .description(func.getDescription())
                        .build()
        ).collect(Collectors.toList());

        return new Page<FunctionInfoVO>()
                .setRecords(records)
                .setTotal(functionPage.getTotal())
                .setCurrent(functionPage.getCurrent())
                .setSize(functionPage.getSize());
    }

    @Override
    public String executeFunction(FunctionExecuteParam param) {
        //查询函数
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()));
        //查询输入参数
        var executeInputParams = functionParamService.list(
                new LambdaQueryWrapper<FunctionParamPO>()
                        .eq(FunctionParamPO::getFunctionId, function.getId())
                        .eq(FunctionParamPO::getCategory, FunctionParamCategoryEnum.INPUT)
                        .orderByAsc(FunctionParamPO::getParamOrder));
        //参数取值
        Map<String, Object> funParamMap = param.getParameters().stream().collect(Collectors.toMap(Parameter::getParamName, Parameter::getParamValue));
        return groovyService.executeGroovy(function.getCode(), funParamMap, executeInputParams);
    }


    @Override
    public Object handle(String functionName, Boolean isPreview, List<String> objectTypes, HashMap<String, Object> parameters)
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
    public List<ParameterMetadataVO> getParameters(String functionName)
            throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException {
        return null;
//        if (functionMapper.selectByApi(functionName) == null) {
//            return null;
//        }
//
//        GroovyClassLoader classLoader = GroovyClassLoaderManager.getIndependentClassLoader();
//        String objectTypes = functionMapper.selectByApi(functionName).getObjectTypes();
//        List<String> objectApiList = new ArrayList<>();
//        if (objectTypes != null && objectTypes.length() > 0) {
//            objectApiList = Arrays.stream(objectTypes.split(",")).collect(Collectors.toList());
//        }
//        //将本体涉及的类都import
//        importAllObjectType(classLoader, objectApiList);
//        GroovyObject functionInstance = getFunctionInstance(classLoader, functionName, false);
//
//        Method handleMethod = FunctionUtils.getMethod(functionInstance, "handle");
//        List<ParameterMetadataVO> params = new ArrayList<>();
//        List<Parameter> parameters = FunctionUtils.getMethodParameterAnnotates(handleMethod);
//        List<OntologyDataTypeEnum> types = FunctionUtils.getParameterTypes(handleMethod);
//        for (int i = 0; i < types.size(); i++) {
//            OntologyDataTypeEnum type = types.get(i);
//            Parameter parameter = parameters.get(i);
//            if (type.isOntologyDataType()) {
//                ParameterMetadataVO vo = new ParameterMetadataVO(parameter.name(), type.name(), parameter.description(), type.getOntologyApi());
//                params.add(vo);
//            } else {
//                ParameterMetadataVO vo = new ParameterMetadataVO(parameter.name(), type.name(), parameter.description());
//                params.add(vo);
//            }
//        }
//        return params;
    }

    private void deleteFunctionGroovy(String functionApi) {
        try {
            //删除groovy文件
            // 构建文件路径
            File rootDir = new File(ROOT_PATH);
            File file = new File(rootDir, functionApi + ".groovy");

            // 检查文件是否存在
            if (file.exists()) {
                // 删除文件
                FileUtils.forceDelete(file);
            }
        } catch (Exception e) {
            log.error("delete function file failed:", e);
            throw new BusinessException("write function to file failed:" + functionApi);
        }
    }

    /***
     * 解析groovy代码，获取参数列表及返回值，批量入库
     * @param functionId
     * @param code
     */
    private void insertBatchFuncParams(Long functionId, String code) {
        //获取groovy参数、返回值信息
        List<FunctionParamDTO> functionParam = groovyService.parseFunctionParam(code);

        if (CollectionUtils.isNotEmpty(functionParam)) {
            List<FunctionParamPO> params = functionParam.stream().map(p ->
                    FunctionParamPO.builder()
                            .functionId(functionId)
                            .paramName(p.getParamName())
                            .paramType(p.getParamType())
                            .category(p.getCategory())
                            .paramSchema(p.getParamSchema())
                            .paramOrder(p.getParamOrder())
                            .typeReferenceName(p.getReferenceType())
                            .createTime(new Date())
                            .updateTime(new Date())
                            .build()
            ).collect(Collectors.toList());
            functionParamService.saveBatch(params);
        }
    }


    private void setOntologyList(List<FunctionVO> functionVOList) {

//        Map<String, OntologyMeta> ontologyMetas = ontologyMetaMapper
//                .selectAllOntologies()
//                .stream()
//                .map((meta) -> new Map.Entry<String, OntologyMeta>() {
//                    @Override
//                    public String getKey() {
//                        return meta.getApiName();
//                    }
//
//                    @Override
//                    public OntologyMeta getValue() {
//                        return meta;
//                    }
//
//                    @Override
//                    public OntologyMeta setValue(OntologyMeta value) {
//                        return meta;
//                    }
//                })
//                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
//
//        functionVOList.forEach(functionVO -> {
//            String objectTypes = functionVO.getObjectTypes();
//            if (objectTypes == null || objectTypes.length() == 0) {
//                return;
//            }
//            String[] ontologyApis = objectTypes.split(",");
//            List<OntologyMetaVO> list = Arrays.stream(ontologyApis)
//                    .map(api -> ontologyMetas.get(api))
//                    .map(meta -> {
//                        OntologyMetaVO vo = new OntologyMetaVO();
//                        BeanUtils.copyProperties(meta, vo);
//                        return vo;
//                    })
//                    .collect(Collectors.toList());
//            functionVO.setOntologyList(list);
//        });
    }


    //如果api在production，则objectType从数据库读取，否则从参数读取。
    private List<String> getObjectApiList(Boolean isPreview, List<String> objectTypes, String functionName) {
//        if (!isPreview) {
//            Function function = functionMapper.selectByApi(functionName);
//            objectTypes = Arrays.asList(function.getObjectTypes().split(","));
//        }
        return objectTypes;
    }

    static private GroovyObject getFunctionInstance(GroovyClassLoader classLoader, String functionName, boolean isPreview)
            throws FunctionFileNotCompiled, FunctionClassNotNewInstanceException, FunctionNotFoundException {
        // File file = getFile(functionName, isPreview);
        File file = null;
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
            return (GroovyObject) groovyClass.newInstance();
        } catch (IllegalAccessException e) {
            throw ExceptionFactory.getFunctionClassNotNewInstanceException(e);
        } catch (InstantiationException e) {
            throw ExceptionFactory.getFunctionClassNotNewInstanceException(e);
        }
    }

    private void importAllObjectType(GroovyClassLoader loader, List<String> objectApis) {
        // TODO: 这个类被加载了3次，需要调试。
        for (String objectApi : objectApis) {
            String className = StringUtil.capitalize(objectApi);
            String classImplString = "" +
                    "package com.aircas.ptr.foundry.ontology;" +
                    "import com.aircas.ptr.foundry.ontology.function.OntologBaseObject; " +
                    "class " + className + "  extends OntologBaseObject {  " +
                    className + "(String primaryKey) { super(\"" + objectApi + "\", primaryKey)}" +
                    "}";
//            log.info("在这里输出本体类信息：" + classImplString);
            loader.parseClass(classImplString);
        }
    }

    private void writeCodeToFile(String functionApi, String code) {

        try {
            // 构建文件路径
            var rootDir = new File(ROOT_PATH);
            var file = new File(rootDir, functionApi + ".groovy");

            // 检查根目录是否存在，如果不存在则创建
            if (!rootDir.exists()) {
                FileUtils.forceMkdir(rootDir);
            }

            // 检查文件是否已存在
            if (file.exists()) {
                throw new IOException("File already exists: " + file.getAbsolutePath());
            }

            // 创建并写入文件
            FileUtils.writeStringToFile(file, code, "UTF-8");
        } catch (Exception e) {
            log.error("write function to file failed:", e);
            throw new BusinessException("write function to file failed:" + functionApi);
        }
    }

}
