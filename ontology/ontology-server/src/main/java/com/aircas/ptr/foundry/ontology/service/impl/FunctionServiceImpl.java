package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.common.constant.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.DuplicatedDataException;
import com.aircas.ptr.foundry.common.util.FileUtil;
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
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.view.FunctionView;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.alibaba.druid.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionServiceImpl extends ServiceImpl<FunctionMapper, Function> implements FunctionService {

    final static String baseDir = "functions";

    @Resource
    private FunctionMapper functionMapper;

    @Resource
    private OntologyActionService ontologyActionService;

    @Resource
    private FunctionParamService functionParamService;

    @Resource
    private GroovyService groovyService;

    @Override
    public void removeByOntologyUniqId(String ontologyUniqId) {
//        var functionList = list(new LambdaQueryWrapper<Function>().eq(Function::getOntologyUniqueIdentif, ontologyUniqId));
//        if(CollectionUtils.isNotEmpty(functionList)) {
//            var funcIds = functionList.stream().map(v -> v.getId()).collect(Collectors.toList());
//            remove(new LambdaQueryWrapper<Function>().in(Function::getId, funcIds));
//            functionParamMapper.delete(new LambdaQueryWrapper<FunctionParamPO>().in(FunctionParamPO::getFunctionId, funcIds));
//        }
    }

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
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi,api));
        var functionParams = functionParamService.list(
                new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId,function.getId()));
        List<FunctionParameterVO> params = CollectionUtils.isEmpty(functionParams) ? new ArrayList<>() :
                functionParams.stream().map(p -> {
                    FunctionParameterVO vo = new FunctionParameterVO();
                    BeanUtils.copyProperties(p, vo);
                    vo.setParamId(p.getId());
                    return vo;
                }).collect(Collectors.toList());
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
    public Boolean deleteByApi(String api) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi,api));
        if(Objects.nonNull(function)){
            //判断函数是否关联了行为，被本体行为使用到则不删除
            var ontologyActions = ontologyActionService.list(
                    new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getFunctionApi,api));
            if(CollectionUtils.isNotEmpty(ontologyActions)){
                return false;
            }
            //删除函数参数记录
            var delParam = functionParamService.remove(
                    new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
            //删除函数记录
            var delFunc = removeById(function.getId());
            //删除groovy文件
            var delGroovy = deleteFunctionGroovy(api);
            return delParam && delFunc && delGroovy;
        }
        return false;
    }

    private boolean deleteFunctionGroovy(String functionName){
        //删除groovy文件
        File file = getFile(functionName, false);
        file.delete();
        return true;
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
    public boolean createFunction(FunctionCreateParam param) {
        var function = Function.builder()
                .api(param.getFunctionApi())
                .description(param.getDescription())
                .displayName(param.getDisplayName())
                .code(param.getCode())
                .type(param.getType())
                .status(1)
                .referenceName(param.getReferenceName())
                .build();
        //api已存在不再插入
        var func = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi,param.getFunctionApi()));
        if(Objects.nonNull(func)){
            return false;
        }
        var result = save(function);
        //解析函数参数，批量入库
        insertBatchFuncParams(function.getId(),param.getCode());
        //生成groovy文件
        write(param.getFunctionApi(),param.getCode(),false);
        return result;
    }

    /***
     * 解析groovy代码，获取参数列表及返回值，批量入库
     * @param functionId
     * @param code
     */
    public void insertBatchFuncParams(Long functionId,String code){
        //获取groovy参数、返回值信息
        List<FunctionParamDTO> functionParam = groovyService.parseFunctionParam(code);
        if(CollectionUtils.isNotEmpty(functionParam)){
            List<FunctionParamPO> params = functionParam.stream().map(p ->
                    FunctionParamPO.builder()
                            .functionId(functionId)
                            .paramName(p.getParamName())
                            .paramType(FunctionParamTypeEnum.valueOf(p.getParamType()))
                            .category(FunctionParamCategoryEnum.valueOf(p.getCategory()))
                            .paramSchema(p.getParamSchema())
                            .paramOrder(p.getParamOrder())
                            .createTime(new Date())
                            .updateTime(new Date())
                            .build()
            ).collect(Collectors.toList());
            functionParamService.insertBatch(params);
        }
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public boolean updateFunction(FunctionUpdateParam param) {
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi,param.getFunctionApi()));
        if(Objects.nonNull(function)){
            //判断函数是否关联了行为，被本体行为使用到则不删除
            var ontologyActions = ontologyActionService.list(
                    new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getFunctionApi,param.getFunctionApi()));
            if(CollectionUtils.isNotEmpty(ontologyActions)){
                return false;
            }
            //groovy代码块未修改，则不修改函数参数
            if(!StringUtils.equals(function.getCode(),param.getCode())){
                //删除函数参数记录
                functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId,function.getId()));
                //解析函数参数，批量入库
                insertBatchFuncParams(function.getId(),param.getCode());
            }
            //更新函数信息
            var updateWrapper = new LambdaUpdateWrapper<Function>().eq(Function::getApi, param.getFunctionApi())
                    .set(Function::getDisplayName, param.getDisplayName())
                    .set(Function::getDescription, param.getDescription())
                    .set(Function::getReferenceName, param.getReferenceName())
                    .set(Function::getCode, param.getCode());
            var updFunc = update(null, updateWrapper);

            //删除groovy文件,并生成新的groovy文件
            var delGroovy = deleteFunctionGroovy(param.getFunctionApi());
            if(delGroovy){
                //生成groovy文件
                write(param.getFunctionApi(),param.getCode(),false);
            }
            return updFunc;
        }
        return false;
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
        var functionList = functionMapper.selectByPage(pageSize, (pageNum - 1) * pageSize);
        var total = functionMapper.selectCount(new LambdaQueryWrapper<>());
        List<FunctionInfoVO> functions = CollectionUtils.isEmpty(functionList) ? new ArrayList<>() : functionList.stream().map(func ->
                FunctionInfoVO.builder()
                        .functionApi(func.getApi())
                        .displayName(func.getDisplayName())
                        .type(func.getType())
                        .description(func.getDescription())
                        .build()
        ).collect(Collectors.toList());
        Page<FunctionInfoVO> result = new Page<>();
        result.setRecords(functions)
                .setSize(pageSize)
                .setCurrent(pageNum)
                .setTotal(total);
        return result;
    }

    @Override
    public void executeFunction(FunctionExecuteParam param) {
        //查询函数
        var function = getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi,param.getFunctionApi()));
        //查询参数
        var funcParams = functionParamService.list(
                new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionId, function.getId()));
        List<FunctionParamPO> paramList = funcParams.stream()
                .filter(p -> FunctionParamCategoryEnum.INPUT.equals(p.getCategory()))
                .sorted(Comparator.comparing(FunctionParamPO::getParamOrder))
                .collect(Collectors.toList());
        groovyService.executeGroovy(function.getCode(),param.getParameters(),paramList);

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

    @Override
    public Boolean write(String functionName, String code, Boolean isPreview) {
        File file = getFile(functionName, isPreview);
        file.delete();
        try {
            if (file.createNewFile()) {
                System.out.println("file created in path" + file.getAbsolutePath());
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
