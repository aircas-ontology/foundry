package com.aircas.ptr.foundry.ontology.application.service.impl;


import com.aircas.ptr.foundry.common.exception.DuplicatedDataException;
import com.aircas.ptr.foundry.common.util.FileUtil;
import com.aircas.ptr.foundry.model.po.Function;
import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.GroovyClassLoaderManager;
import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.FunctionVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.repository.dao.FunctionMapper;
import groovy.lang.GroovyObject;
import lombok.RequiredArgsConstructor;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


@Service
@RequiredArgsConstructor

public class FunctionServiceImpl implements FunctionService {

    static String baseDir = "functions";

    @Resource
    FunctionMapper functionMapper;

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
    public FunctionVO getFunctionByApi(String api) {
        Function function = functionMapper.selectByApi(api);
        FunctionVO functionVO = new FunctionVO();
        BeanUtils.copyProperties(function, functionVO);
        return functionVO;
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
        return retResult;
    }

    @Override
    public Object handle(String functionName, Boolean isPreview, HashMap<String, Object> parameters) {
        Object result = null;
        try {
            File file = getFile(functionName, isPreview);
            Class groovyClass = GroovyClassLoaderManager.getIndependentClassLoader().parseClass(file);
            GroovyObject groovyObject = (GroovyObject)groovyClass.newInstance();
            result = groovyObject.invokeMethod("handle", parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public Boolean write(String functionName, String code, Boolean isPreview) {
        File file = getFile(functionName, isPreview);
        if (isPreview) {
            file.delete();
        }
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
            String decodeCode = URLEncoder.encode(code, "UTF-8");
            return decodeCode;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }



    private File getFile(String functionName, Boolean isPreview) {
        this.createFunctionFoldersIfNeeded();
        String fileName = functionName + ".groovy";
        File path;
        if (isPreview) {
            path = FileUtils.getFile(baseDir, "preview", fileName);
        } else {
            path = FileUtils.getFile(baseDir, fileName);
        }
        return path;
    }

    private void createFunctionFoldersIfNeeded() {
        if (FileUtil.allFiles(baseDir) == null) {
            FileUtil.createDir(baseDir);
            FileUtil.createDir(FileUtils.getFile(baseDir, "preview").getPath());
        }
    }
}
